/*
	based on GPL2 library by hamoid
	https://github.com/hamoid/video_export_processing
*/

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;

import processing.core.PApplet;
import processing.core.PImage;

public class VideoExport {
  protected Process process;
  protected OutputStream ffmpeg;
  
  protected PApplet parent;
  protected PImage img;
  protected byte[] pixelsByte = null;
  
  String outputFileName = "video.mp4";
  int ffmpegCrfQuality = 15;
  float ffmpegFrameRate = 30f;
  String ffmpegPath = "/usr/bin/ffmpeg";
  
  boolean saveDebugInfo = false;

  public VideoExport(PApplet parent) {
    parent.registerMethod("dispose", this);

    this.parent = parent;
    this.img = parent.g;
  }

  public void initialize() {
    File ffmpegFile = new File(ffmpegPath);
    
    if (!ffmpegFile.isFile()) {
      PApplet.println("ffmpeg path is wrong!");
      return;
    }
    
    if (img.pixelWidth == 0 || img.pixelHeight == 0) {
      PApplet.println("The export image size is 0!");
      return;
    }

    if (img.pixelWidth % 2 == 1 || img.pixelHeight % 2 == 1) {
      PApplet.println("Width and height can only be even numbers when using the h264 encoder\n"
        + "but the requested image size is " + img.pixelWidth + "x"
        + img.pixelHeight);
      return;
    }
    
    startFfmpeg(ffmpegPath);
  }

  // ffmpeg -i input -c:v libx264 -crf 20 -maxrate 400k -bufsize 1835k
  // output.mp4 -profile:v baseline -level 3.0
  // https://trac.ffmpeg.org/wiki/Encode/H.264#Compatibility
  protected void startFfmpeg(String executable) {
    // -y = overwrite, otherwise it fails the second time you run
    // -an = no audio
    // "-b:v", "3000k" = video bit rate
    // "-i", "-" = pipe:0
    
    String command = String.format("/usr/bin/ffmpeg -y -f rawvideo -vcodec rawvideo -s %dx%d -pix_fmt rgb24 -r %d -i - -an -vcodec h264 -pix_fmt yuv420p -crf %d %s", img.pixelWidth, img.pixelHeight, (int)(ffmpegFrameRate), ffmpegCrfQuality, parent.sketchPath(outputFileName));
    String[] cmdArgs = command.split(" ");
    PApplet.println(cmdArgs);
    ProcessBuilder processBuilder = new ProcessBuilder(cmdArgs);
    
    if (saveDebugInfo) {
      processBuilder.redirectErrorStream(true);
      File ffmpegOutputLog = new File(parent.sketchPath("ffmpeg.txt"));
      processBuilder.redirectOutput(ffmpegOutputLog);
      processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
    }
    
    try {
      process = processBuilder.start();
    }
    catch (Exception e) {
      e.printStackTrace();
      PApplet.println("Unable to start process. Please check the debug output file for info.");
    }

    ffmpeg = process.getOutputStream();
  }

  /**
   * Adds one frame to the video file. The frame will be the content of the
   * display, or the content of a PImage if you specified one in the
   * constructor.
   */
  public void saveFrame(boolean loadPixels) {
    if (img != null && img.width > 0) {
      if (pixelsByte == null) {
        pixelsByte = new byte[img.pixelWidth * img.pixelHeight * 3];
      }
      if (loadPixels) {
        img.loadPixels();
      }

      int byteNum = 0;
      for (final int px : img.pixels) {
        pixelsByte[byteNum++] = (byte) (px >> 16);
        pixelsByte[byteNum++] = (byte) (px >> 8);
        pixelsByte[byteNum++] = (byte) (px);
      }

      try {
        ffmpeg.write(pixelsByte);
      }
      catch (Exception e) {
        e.printStackTrace();
        PApplet.println("Error. Please check the debug output file for info.");
      }
    }
  }

  /**
   * Called automatically by Processing to clean up before shut down
   */
  public void dispose() {
    if (ffmpeg != null) {
      try {
        ffmpeg.flush();
        ffmpeg.close();
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      ffmpeg = null;
    }
    if (process != null) {
      try {
        process.destroy();
        process.waitFor();

        PApplet.println(parent.sketchPath(outputFileName), "saved.");
      }
      catch (InterruptedException e) {
        PApplet.println("Waiting for ffmpeg timed out!");
        e.printStackTrace();
      }
      process = null;
    }
  }
}
