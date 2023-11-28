package com.chatbot.controller;

import com.chatbot.service.TransliterateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "Transliterate", description = "Romanized text to native script")
public class TransliterateController {

  private final TransliterateService transliterateService;

  @Operation(summary = "Transliterate a romanized word to native script")
  @GetMapping("/transliterate")
  public ResponseEntity<Map<String, Object>> transliterate(
      @RequestParam @NotBlank(message = "{validation.text.required}") String text,
      @RequestParam(defaultValue = "te")
          @Pattern(regexp = "te", message = "{validation.lang.unsupported}") String lang) {
    return ResponseEntity.ok(Map.of("suggestions", transliterateService.transliterate(text, lang)));
  }
}
