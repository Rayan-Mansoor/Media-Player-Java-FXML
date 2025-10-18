package com.mycompany.mediaplayer;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Slider;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Behavior-focused controller (no UI/CSS changes).
 * - Cancel-safe open + last-folder memory
 * - HUD auto-hide with single PauseTransition
 * - Proper progress max + clamped seeks
 * - Scrub preview while dragging; seek on release
 * - Double-click to play/pause
 * - Keyboard shortcuts via Scene event filter (work regardless of HUD
 * visibility)
 * - Live icon updates via status/mute listeners
 */
public class PrimaryController implements Initializable {

    // ---------- Constants ----------
    private static final int SEEK_STEP_SECONDS = 10;
    private static final int VOLUME_STEP_PERCENT = 5;
    private static final String PREF_LAST_DIR = "lastMediaDir";
    private static final String APP_TITLE_SUFFIX = " — Youtube Inspired Media Player By Group 14";

    // ---------- State ----------
    private MediaPlayer mp;
    private RotateTransition rotate; // for settings "gear"
    private final PauseTransition hudHider = new PauseTransition(Duration.seconds(3));
    private final Preferences prefs = Preferences.userNodeForPackage(PrimaryController.class);

    private boolean isScrubPreviewActive = false;
    private boolean hudVisible = false; // <— master HUD visibility flag
    private String lastStageTitle = null;

    // ---------- FXML nodes ----------
    @FXML
    private Label currentTime, totalTime;
    @FXML
    private AnchorPane app; // root container (has the Scene)
    @FXML
    private ImageView MPicon;
    @FXML
    private MediaView MV;
    @FXML
    private Button PlayPause, PlayPausePic, VolumeOn, VolumeOff, forward, backward;
    @FXML
    private MenuButton settings;
    @FXML
    private Slider Progress, VolumeBar;
    @FXML
    private HBox TimeBox;

    // ---------- Initialization ----------
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Gear rotation
        rotate = new RotateTransition(Duration.seconds(2), settings);
        rotate.setCycleCount(RotateTransition.INDEFINITE);
        rotate.setByAngle(360);

        // MediaView sizing (preserve original layout behavior)
        MV.setPreserveRatio(true);
        MV.fitWidthProperty().unbind();
        MV.fitHeightProperty().unbind();
        MV.fitWidthProperty().bind(Bindings.selectDouble(MV.sceneProperty(), "width"));
        MV.fitHeightProperty().bind(Bindings.selectDouble(MV.sceneProperty(), "height"));

        // One reusable HUD hider
        hudHider.setOnFinished(e -> setControlsVisible(false));
        setControlsVisible(false); // start hidden

        // Sliders
        Progress.setMin(0);
        Progress.setMax(100); // true max set on media ready
        VolumeBar.setMin(0);
        VolumeBar.setMax(100);
        VolumeBar.setValue(100);

        // Volume changes
        VolumeBar.valueProperty().addListener((obs, ov, nv) -> {
            if (mp != null)
                mp.setVolume(nv.doubleValue() / 100.0);
        });

        // Scrub behavior
        Progress.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (mp == null)
                return;
            if (isChanging) {
                isScrubPreviewActive = true;
                double previewSec = Progress.getValue();
                currentTime.setText(formatTime(Duration.seconds(previewSec)));
            } else {
                isScrubPreviewActive = false;
                mp.seek(Duration.seconds(Progress.getValue()));
            }
        });

        // Click-to-seek
        Progress.setOnMousePressed(e -> {
            if (mp != null)
                mp.seek(Duration.seconds(Progress.getValue()));
        });

        // Keyboard shortcuts via event filter (works even if focused node consumes
        // keys)
        app.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                    KeyCode code = ev.getCode();
                    switch (code) {
                        case SPACE:
                            showHudTemporarily();
                            play_pause();
                            ev.consume();
                            break;
                        case RIGHT:
                            showHudTemporarily();
                            Forward10sec();
                            ev.consume();
                            break;
                        case LEFT:
                            showHudTemporarily();
                            Backward10sec();
                            ev.consume();
                            break;
                        case UP:
                            showHudTemporarily();
                            nudgeVolume(+VOLUME_STEP_PERCENT);
                            ev.consume();
                            break;
                        case DOWN:
                            showHudTemporarily();
                            nudgeVolume(-VOLUME_STEP_PERCENT);
                            ev.consume();
                            break;
                        case M:
                            showHudTemporarily();
                            Volume(); // toggle mute
                            ev.consume();
                            break;
                        case F:
                            toggleFullScreen();
                            ev.consume();
                            break;
                        default:
                            // no-op
                    }
                });
            }
        });

        // Double-click video to play/pause (and show HUD briefly)
        MV.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                showHudTemporarily();
                play_pause();
                e.consume();
            }
        });
    }

    // ---------- File open ----------
    @FXML
    private void Open() {
        StopGear();

        FileChooser fc = new FileChooser();
        fc.setTitle("Open Media File");

        // Remember last directory
        String lastDirPath = prefs.get(PREF_LAST_DIR, null);
        if (lastDirPath != null) {
            File lastDir = new File(lastDirPath);
            if (lastDir.exists() && lastDir.isDirectory()) {
                fc.setInitialDirectory(lastDir);
            }
        }

        // Broad media extensions
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Media Files",
                        "*.mp4", "*.mkv", "*.avi", "*.mov",
                        "*.mp3", "*.wav", "*.m4a", "*.aac"));

        Stage owner = (Stage) app.getScene().getWindow();
        File mf = fc.showOpenDialog(owner);
        if (mf == null)
            return; // canceled

        // Persist last dir
        File parent = mf.getParentFile();
        if (parent != null)
            prefs.put(PREF_LAST_DIR, parent.getAbsolutePath());

        String path = mf.toURI().toString();

        // Dispose old player
        if (mp != null) {
            mp.stop();
            mp.dispose();
            mp = null;
        }

        MPicon.setVisible(false);

        try {
            mp = new MediaPlayer(new Media(path));
        } catch (MediaException mex) {
            System.err.println("Failed to load media: " + mex.getMessage());
            return;
        }

        MV.setMediaPlayer(mp);

        // Reset UI state
        currentTime.setText("--:--");
        totalTime.setText("--:--");
        Progress.setValue(0);

        // Stage title
        setStageTitle(mf.getName() + APP_TITLE_SUFFIX);

        // Ready
        mp.setOnReady(() -> {
            Duration total = mp.getTotalDuration();
            double totalSec = total.toSeconds();
            Progress.setMax(totalSec > 0 ? totalSec : 100.0);
            totalTime.setText(formatTime(total));
            VolumeBar.setValue(mp.getVolume() * 100.0);
            setControlsVisible(true);
        });

        // Sync time/slider
        mp.currentTimeProperty().addListener((o, ov, now) -> {
            if (!Progress.isValueChanging()) {
                Progress.setValue(now.toSeconds());
                if (!isScrubPreviewActive)
                    currentTime.setText(formatTime(now));
            }
        });

        // End of media
        mp.setOnEndOfMedia(() -> {
            mp.pause();
            mp.seek(Duration.ZERO);
            reflectPlayState(); // swap icons if HUD visible
        });

        mp.setOnError(() -> System.err.println("Media error: " + mp.getError()));

        // Live state listeners for icons
        wirePlayerStateListeners();

        mp.play();
        reflectPlayState();
        reflectMuteState();
    }

    // ---------- Playback controls ----------
    @FXML
    private void play_pause() {
        if (mp == null)
            return;
        StopGear();

        boolean wasPlaying = (mp.getStatus() == MediaPlayer.Status.PLAYING);
        if (wasPlaying) {
            mp.pause();
            if (hudVisible) { // optimistic only if HUD is visible
                PlayPause.setVisible(false);
                PlayPausePic.setVisible(true);
            }
        } else {
            mp.play();
            if (hudVisible) {
                PlayPause.setVisible(true);
            }
            if (hudVisible) {
                PlayPausePic.setVisible(false);
            }
        }
        // status listener will reconfirm
    }

    @FXML
    private void twoX() {
        setRate(2.0);
    }

    @FXML
    private void oneX() {
        setRate(1.0);
    }

    @FXML
    private void halfX() {
        setRate(0.5);
    }

    @FXML
    private void threequaterX() {
        setRate(0.75);
    } // name kept to match FXML

    private void setRate(double x) {
        if (mp == null)
            return;
        mp.setRate(x);
        rotate.pause();
    }

    @FXML
    private void SettingIcon() {
        if (rotate.getStatus() == Animation.Status.RUNNING)
            rotate.pause();
        else
            rotate.play();
    }

    @FXML
    private void StopGear() {
        rotate.pause();
    }

    @FXML
    private void Volume() {
        if (mp == null)
            return;
        StopGear();
        mp.setMute(!mp.isMute());
        if (hudVisible)
            reflectMuteState(); // optimistic only if HUD visible
    }

    @FXML
    private void Forward10sec() {
        if (mp == null)
            return;
        StopGear();
        seekBySeconds(+SEEK_STEP_SECONDS);
    }

    @FXML
    private void Backward10sec() {
        if (mp == null)
            return;
        StopGear();
        seekBySeconds(-SEEK_STEP_SECONDS);
    }

    private void seekBySeconds(int delta) {
        if (mp == null)
            return;
        double now = mp.getCurrentTime().toSeconds();
        double total = safeTotalSeconds();
        double next = clamp(now + delta, 0, total);
        mp.seek(Duration.seconds(next));
    }

    private double safeTotalSeconds() {
        return mp != null && mp.getTotalDuration() != null
                ? Math.max(0, mp.getTotalDuration().toSeconds())
                : 0;
    }

    private void nudgeVolume(int deltaPercent) {
        if (mp == null)
            return;
        double newVol = clamp(mp.getVolume() * 100.0 + deltaPercent, 0, 100);
        VolumeBar.setValue(newVol); // listener will set player volume
    }

    private void toggleFullScreen() {
        Stage st = (Stage) app.getScene().getWindow();
        st.setFullScreen(!st.isFullScreen());
    }

    // ---------- HUD show/hide ----------
    @FXML
    private void ShowNodes() {
        if (mp == null)
            return;
        setControlsVisible(true);
        hudHider.stop();
        hudHider.playFromStart();
    }

    private void showHudTemporarily() {
        if (mp == null)
            return;
        setControlsVisible(true);
        hudHider.stop();
        hudHider.playFromStart();
    }

    private void setControlsVisible(boolean visible) {
        hudVisible = visible;

        Progress.setVisible(visible);
        settings.setVisible(visible);
        forward.setVisible(visible);
        backward.setVisible(visible);
        VolumeBar.setVisible(visible);
        TimeBox.setVisible(visible);

        // Respect master flag for paired icons
        reflectPlayState();
        reflectMuteState();
    }

    private void reflectPlayState() {
        if (!hudVisible || mp == null) {
            PlayPause.setVisible(false);
            PlayPausePic.setVisible(false);
            return;
        }
        boolean playing = mp.getStatus() == MediaPlayer.Status.PLAYING;
        PlayPause.setVisible(playing);
        PlayPausePic.setVisible(!playing);
    }

    private void reflectMuteState() {
        if (!hudVisible || mp == null) {
            VolumeOn.setVisible(false);
            VolumeOff.setVisible(false);
            return;
        }
        boolean muted = mp.isMute();
        VolumeOn.setVisible(!muted);
        VolumeOff.setVisible(muted);
    }

    // ---------- Live state listeners ----------
    private void wirePlayerStateListeners() {
        if (mp == null)
            return;

        mp.statusProperty().addListener((obs, oldSt, newSt) -> {
            reflectPlayState();
        });

        mp.muteProperty().addListener((obs, oldMuted, newMuted) -> {
            reflectMuteState();
        });
    }

    // ---------- Utilities ----------
    private static String formatTime(Duration time) {
        long secs = Math.max(0, (long) Math.floor(time.toSeconds()));
        long hours = secs / 3600;
        long minutes = (secs % 3600) / 60;
        long seconds = secs % 60;
        return (hours > 0)
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private void setStageTitle(String title) {
        Stage st = (Stage) app.getScene().getWindow();
        st.setTitle(title);
    }
}