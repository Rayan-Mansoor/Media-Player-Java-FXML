package com.mycompany.mediaplayer;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Slider;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.util.Duration;

public class PrimaryController implements Initializable {
    
    static int count=0;
    
    MediaPlayer mp;
    RotateTransition rotate;
    
    @FXML
    Label currentTime,totalTime;
    
    @FXML
    AnchorPane app;
    
    @FXML
    ImageView MPicon;
    
    @FXML
    MediaView MV;
    
    @FXML
    Button PlayPause, PlayPausePic, VolumeOn, VolumeOff, forward, backward;
    
    @FXML
    MenuButton settings;
    
    @FXML
    Slider Progress, VolumeBar;
    
    @FXML
    HBox TimeBox;

    public void Open(){
        StopGear();
        FileChooser fc = new FileChooser();
        File mf = fc.showOpenDialog(null);
        String path = mf.toURI().toString();
        if(mp != null){
            mp.dispose();
        }
        if(path!= null){
            MPicon.setVisible(false);
            Media m = new Media(path);
            mp = new MediaPlayer(m);
            MV.setMediaPlayer(mp);
            DoubleProperty width = MV.fitWidthProperty();
            DoubleProperty height = MV.fitHeightProperty();
            width.bind(Bindings.selectDouble(MV.sceneProperty(), "width"));
            height.bind(Bindings.selectDouble(MV.sceneProperty(), "height")); 
            mp.currentTimeProperty().addListener(new ChangeListener<Duration>(){
                @Override
                public void changed(ObservableValue<? extends Duration> ov, Duration t, Duration t1) {
                    Progress.setValue(t1.toSeconds());
                    
                }
            });
            
            Progress.setOnMousePressed(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent t) {
                    mp.seek(Duration.seconds(Progress.getValue()));
                }
            });
            
           Progress.setOnMouseDragged(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent t) {
                    mp.seek(Duration.seconds(Progress.getValue()));
                }
            });
           
           VolumeBar.setValue(mp.getVolume()*100);
           VolumeBar.valueProperty().addListener(new InvalidationListener() {
                @Override
                public void invalidated(Observable o) {
                    mp.setVolume(VolumeBar.getValue()/100);
                }
            });
           BindCurrentTimeLabel();
            mp.play();
        } 
    }
    
     public void setMaxTime(){
        String maxTime = getTime(mp.getTotalDuration());
        totalTime.setText(maxTime);
    }
    
    public void BindCurrentTimeLabel(){
        currentTime.textProperty().bind(Bindings.createStringBinding(new Callable<String>() {
            @Override
            public String call() throws Exception {
                setMaxTime();
                return getTime(mp.getCurrentTime());
            }
        }, mp.currentTimeProperty()));
    }
    
    private String getTime(Duration time){
        int hrs = (int) time.toHours();
        int min = (int) time.toMinutes();
        int sec = (int) time.toSeconds();
        
        if(sec > 59){
            sec%=60;
        }
        if(min > 59){
            min%=60;
        }
        if(hrs > 59){
            hrs%=60;
        }
        
        if(hrs > 0){
            return String.format("%d:%02d:%02d", hrs, min, sec);
        }
        
        else{
            return String.format("%02d:%02d", min,sec);
        }
    }
    
    public void play_pause(){
        if(mp==null){
            return;
        }
        StopGear();
        MediaPlayer.Status MPstatus = mp.getStatus();
        if(mp.getStatus() == MediaPlayer.Status.PLAYING){
           PlayPause.setVisible(false);
           PlayPausePic.setVisible(true);
            mp.pause();
        }
        else if (mp.getStatus() == MediaPlayer.Status.PAUSED){
            mp.play();
            PlayPause.setVisible(true);
            PlayPausePic.setVisible(false);
        }
    }
    
    public void twoX(){
        double v = 2;
        setRate(v);
    }
    
    public void oneX(){
        double v = 1;
        setRate(v);
    }
    
    public void halfX(){
        double v = 0.5;
        setRate(v);
    }
    
    public void threequaterX(){
        double v = 0.75;
        setRate(v);
    }
    
    private void setRate(double x){
        mp.setRate(x);
        rotate.pause();
    }
    
    public void SettingIcon(){
        RotateTransition.Status status = rotate.getStatus();
        if(status == RotateTransition.Status.STOPPED || status == RotateTransition.Status.PAUSED){
            rotate.play();
        }
        else if(status == RotateTransition.Status.RUNNING){
            rotate.pause();
        }
    }
    
    public void StopGear(){
        rotate.pause();
    }

    public void ShowNodes(){
        if(mp==null){
            return;
        }
        //setMaxTime();
        count++;
        if(mp.getStatus() == MediaPlayer.Status.PLAYING){
           PlayPause.setVisible(true);
           PlayPausePic.setVisible(false);
        }
        if(mp.getStatus() == MediaPlayer.Status.PAUSED){
           PlayPause.setVisible(false);
           PlayPausePic.setVisible(true);
        }
        Progress.setVisible(true);
        if(mp.isMute()){
            VolumeOn.setVisible(false);
            VolumeOff.setVisible(true);
        }
        else if(!mp.isMute()){
            VolumeOn.setVisible(true);
            VolumeOff.setVisible(false);
        }
        settings.setVisible(true);
        forward.setVisible(true);
        backward.setVisible(true);
        VolumeBar.setVisible(true);
        TimeBox.setVisible(true);
        IsStillMoving(count);

    }
        
        private void IsStillMoving(int x){
            PauseTransition pause = new PauseTransition(Duration.seconds(3));
            pause.setOnFinished(e -> { 
                if(x == count){
                    Progress.setVisible(false);
                    PlayPause.setVisible(false);
                    PlayPausePic.setVisible(false);
                    VolumeOn.setVisible(false);
                    VolumeOff.setVisible(false);
                    settings.setVisible(false);
                    forward.setVisible(false);
                    backward.setVisible(false);
                    VolumeBar.setVisible(false);
                    TimeBox.setVisible(false);
                }
        });
        pause.play();
        }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        rotate = new RotateTransition(Duration.seconds(2), settings);
        rotate.setCycleCount(Animation.INDEFINITE);
       // rotate.setInterpolator(Interpolator.LINEAR);
        rotate.setByAngle(360);
    }
    
    public void Volume(){
        if(mp==null){
            return;
        }
        StopGear();
        if(!mp.isMute()){
            VolumeOn.setVisible(false);
            VolumeOff.setVisible(true);
            mp.setMute(true);
        }
        else if(mp.isMute()){
            VolumeOn.setVisible(true);
            VolumeOff.setVisible(false);
            mp.setMute(false);
        }
        
    }
    
    public void Forward10sec(){
        if(mp==null){
            return;
        }
        StopGear();
        mp.seek(mp.getCurrentTime().add(Duration.seconds(10)));
    }
    
    public void Backward10sec(){
        if(mp==null){
            return;
        }
        StopGear();
        mp.seek(mp.getCurrentTime().subtract(Duration.seconds(10)));
    }
}
