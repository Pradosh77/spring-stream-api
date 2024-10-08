package com.stream.app.services.impl;

import com.stream.app.entities.Video;
import com.stream.app.repositories.VideoRepository;
import com.stream.app.services.VideoService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class VideoServiceImpl implements VideoService {

    @Value("${files.video}")
    String DIR;
    @Value("${files.video.hls}")
    String HLS_DIR;

    @Autowired
    private VideoRepository videoRepository;

    @PostConstruct
    public void init(){
        File file = new File(DIR);

        try {
            Files.createDirectories(Paths.get(HLS_DIR));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        if(!file.exists()){
            file.mkdir();
            System.out.println("Folder Created");
        }
        else
            System.out.println("Folder already created");
    }

    @Override
    public Video save(Video video, MultipartFile file) {
        try{
            String fileName = file.getOriginalFilename();
            String contentType = file.getContentType();
            InputStream inputStream = file.getInputStream();

            //folder path :-create
            String cleanFileName = StringUtils.cleanPath(fileName);
            String cleanFolder = StringUtils.cleanPath(DIR);

            //folder path with file name
            Path path = Paths.get(cleanFolder, cleanFileName);
            System.out.println(path);

            //copy file to the folder
            Files.copy(inputStream,path, StandardCopyOption.REPLACE_EXISTING);

            //video file meta data
            video.setContentType(contentType);
            video.setFilePath(path.toString());

            Video savedVideo = videoRepository.save(video);
            //processing video
            //processVideo(savedVideo.getVideoId());

            //delete actual video file and database entry  if exception

            // metadata save
            return savedVideo;

        }catch(IOException e){
            e.printStackTrace();
            return null;
        }

    }

    @Override
    public Video get(String videoId) {
        return videoRepository.findById(videoId).orElseThrow(() -> new RuntimeException("video not found"));
    }

    @Override
    public Video getByTitle(String title) {
        return null;
    }

    @Override
    public List<Video> getAll() {
        return videoRepository.findAll();
    }

    @Override
    public String processVideo(String videoId) {
        Video video = this.get(videoId);
        String filePath = video.getFilePath();

        // Path to the original video file
        Path videoPath = Paths.get(filePath);

        try {
            // Create the output directory for HLS segments
            Path outputPath = Paths.get(HLS_DIR, videoId);
            Files.createDirectories(outputPath);

            // Construct the FFmpeg command for HLS conversion
            String ffmpegCmd = String.format(
                    "ffmpeg -i \"%s\" -c:v libx264 -c:a aac -strict -2 -f hls -hls_time 180 -hls_list_size 0 -hls_segment_filename \"%s/segment_%%03d.ts\" \"%s/master.m3u8\"",
                    videoPath, outputPath, outputPath
            );

            System.out.println("Executing FFmpeg command: " + ffmpegCmd);

            // Execute the command using ProcessBuilder
            ProcessBuilder processBuilder = new ProcessBuilder("ffmpeg", "-i", videoPath.toString(), "-c:v", "libx264", "-c:a", "aac", "-strict", "-2", "-f", "hls",
                    "-hls_time", "10", "-hls_list_size", "0",
                    "-hls_segment_filename", String.format("%s/segment_%%03d.ts", outputPath.toString()),
                    String.format("%s/master.m3u8", outputPath.toString()));

            processBuilder.inheritIO();
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException("Video processing failed!!");
            }

            return videoId;

        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException("Video processing failed!!", ex);
        }
    }
}
