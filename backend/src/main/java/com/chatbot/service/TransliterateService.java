package com.chatbot.service;

import java.util.List;

public interface TransliterateService {

  List<String> transliterate(String text, String lang);
}
