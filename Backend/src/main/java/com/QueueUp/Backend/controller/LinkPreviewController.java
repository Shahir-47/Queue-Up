package com.QueueUp.Backend.controller;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/link-preview")
public class LinkPreviewController {

    @PostMapping
    public ResponseEntity<?> getLinkPreview(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null) return ResponseEntity.badRequest().build();

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0") // Pretend to be a browser so sites dont block us
                    .timeout(5000)
                    .get();

            Map<String, String> data = new HashMap<>();

            // Try opengraph tags first
            data.put("title", getMetaTag(doc, "og:title"));
            data.put("description", getMetaTag(doc, "og:description"));
            data.put("image", getMetaTag(doc, "og:image"));
            data.put("url", url);

            // Fallback to standard html tags if opengraph is missing
            if (data.get("title").isEmpty()) data.put("title", doc.title());
            if (data.get("description").isEmpty()) {
                data.put("description", getMetaTag(doc, "description"));
            }

            return ResponseEntity.ok(data);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Could not fetch preview"));
        }
    }

    private String getMetaTag(Document doc, String attr) {
        // Select meta tag
        var element = doc.select("meta[property='" + attr + "'], meta[name='" + attr + "']").first();
        return (element != null) ? element.attr("content") : "";
    }
}