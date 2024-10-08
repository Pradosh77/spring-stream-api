package com.stream.app.controllers;


import com.stream.app.AppConstant;
import com.stream.app.entities.Video;
import com.stream.app.payload.CustomMessage;
import com.stream.app.services.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("api/videos")
public class VideoController {

    @Autowired
    private VideoService videoService;


    @PostMapping("/upload")
    public ResponseEntity<?> create(@RequestParam("file") MultipartFile file,
                                                @RequestParam("title") String title,
                                                @RequestParam("description") String description ){

        Video video = new Video();
        video.setTitle(title);
        video.setDescription(description);
        video.setVideoId(UUID.randomUUID().toString());

        Video savedVideo = videoService.save(video,file);

        if(savedVideo != null){
            return ResponseEntity.status(HttpStatus.OK).body(savedVideo);
        } else {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CustomMessage
                            .builder()
                            .message("Video not uploaded")
                            .success(false)
                            .build());
        }
    }

    //get all video
    @GetMapping("/videoList")
    public List<Video> getVideoList(){
        return videoService.getAll();
    }

    //stream video
    @GetMapping("/stream/{videoId}")
    public ResponseEntity<Resource> stream(
            @PathVariable String videoId
    ){
        Video video = videoService.get(videoId);

        String contentType = video.getContentType();
        String filePath = video.getFilePath();

        Resource resource = new FileSystemResource(filePath);

        if(contentType == null){
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    //stream video by ranges
    @GetMapping("/stream/range/{videoId}")
    public ResponseEntity<Resource> streamVideoRange(
        @PathVariable String videoId,
        @RequestHeader(value = "Range",required = false) String range
    ){
        Video video = videoService.get(videoId);
        String contentType = video.getContentType();

        if(contentType == null){
            contentType = "application/octet-stream";
        }

        Path path = Paths.get(video.getFilePath());
        Resource resource = new FileSystemResource(path);

        //video file length;
        long fileLength = path.toFile().length();

        if(range == null){
            return ResponseEntity
                    .ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        }

// for start rang to end range chunks after split opration
//        if (ranges.length > 1) {
//            rangeEnd = Long.parseLong(ranges[1]);
//        } else {
//            rangeEnd = fileLength - 1;
//        }
//
//        if (rangeEnd > fileLength - 1) {
//            rangeEnd = fileLength - 1;
//        }


        String[] ranges = range.replace("bytes=","").split("-");
        long startRange = Long.parseLong(ranges[0]);
        long endRange = startRange + AppConstant.CHUNK_SIZE - 1;
        if(endRange >= fileLength){
            endRange = fileLength -1;
        }

        System.out.println("range Start :" + startRange);
        System.out.println("range End :" + endRange);

        InputStream inputStream;

        try {
            inputStream = Files.newInputStream(path);
            inputStream.skip(startRange);
            long contentLength = endRange - startRange + 1;

            byte[] bufferData = new byte[(int) contentLength];
            int read = inputStream.read(bufferData,0,bufferData.length);
            System.out.println("read(No of bytes read)" + read);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Range", "bytes " + startRange + "-" + endRange + "/" + fileLength);
            headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");
            headers.add("X-Content-Type-Options", "nosniff");
            headers.setContentLength(contentLength);

            return ResponseEntity
                    .status(HttpStatus.PARTIAL_CONTENT)
                    .headers(headers)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new ByteArrayResource(bufferData));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }


    }

    //serve hls playlist

    //master.m2u8 file

    @Value("${files.video.hls}")
    private String HLS_DIR;

    @GetMapping("/{videoId}/master.m3u8")
    public ResponseEntity<Resource> serverMasterFile(
            @PathVariable String videoId
    ) {

//        creating path
        Path path = Paths.get(HLS_DIR, videoId, "master.m3u8");

        System.out.println(path);

        if (!Files.exists(path)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Resource resource = new FileSystemResource(path);

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl"
                )
                .body(resource);


    }

    //serve the segments

    @GetMapping("/{videoId}/{segment}.ts")
    public ResponseEntity<Resource> serveSegments(
            @PathVariable String videoId,
            @PathVariable String segment
    ) {

        // create path for segment
        Path path = Paths.get(HLS_DIR, videoId, segment + ".ts");
        if (!Files.exists(path)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Resource resource = new FileSystemResource(path);

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.CONTENT_TYPE, "video/mp2t"
                )
                .body(resource);

    }


}
