package org.example;

import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.example.CustomCostSimulation.runEval;
import static org.example.CustomCostSimulation.runSimulationMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import java.nio.file.*;
import java.io.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class SchedulingController {


    @PostMapping("/schedule")
    public ResponseEntity<Map<String, Object>> scheduleTask(@RequestBody SchedulingRequest request) {
        // Dummy response for now
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Request received successfully");
        response.put("algorithm", request.getAlgname());
        response.put("dataset", request.getDataset());
        response.put("evaluation", request.isIs_eval_process());
        Map<String, String> results = runSimulationMap(request.getAlgname(), request.getDataset(), request.isIs_eval_process());

        response.put("result", results);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/eval")
    public ResponseEntity<Map<String, Object>> runEvaluation() {
        runEval();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Evaluation completed");
        
        return ResponseEntity.ok(response);
    }


    @GetMapping("/eval/metrics")
    public ResponseEntity<List<Map<String, String>>> getMetrics() throws IOException {
        List<Map<String, String>> metrics = new ArrayList<>();
        
        // Read CSV file
        Path csvPath = Paths.get("outputs/metrics.csv");
        List<String> lines = Files.readAllLines(csvPath);
        
        if (lines.isEmpty()) {
            return ResponseEntity.ok(metrics);
        }
        
        // Parse header
        String[] headers = lines.get(0).split(",");
        
        // Parse data rows
        for (int i = 1; i < lines.size(); i++) {
            String[] values = lines.get(i).split(",");
            Map<String, String> row = new HashMap<>();
            
            for (int j = 0; j < headers.length; j++) {
                row.put(headers[j], j < values.length ? values[j] : "");
            }
            metrics.add(row);
        }
        
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/eval/images")
    public ResponseEntity<List<String>> getImageList() throws IOException {
        Path imagesDir = Paths.get("outputs/images");
        
        if (!Files.exists(imagesDir)) {
            return ResponseEntity.ok(new ArrayList<>());
        }
        
        List<String> imageNames = Files.list(imagesDir)
            .filter(path -> path.toString().matches(".*\\.(png|jpg|jpeg|gif)"))
            .map(path -> path.getFileName().toString())
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(imageNames);
    }

    @GetMapping("/eval/images/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) throws IOException {
        Path imagePath = Paths.get("outputs/images").resolve(filename);
        Resource resource = new UrlResource(imagePath.toUri());
        System.out.println("Serving image: " + imagePath.toString());
        if (resource.exists() && resource.isReadable()) {
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
