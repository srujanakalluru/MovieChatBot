
package com.chatbot.service;

import com.chatbot.dto.JsonObjectWrapper;
import org.springframework.stereotype.Service;

@Service
public interface MovieIntelService {
  JsonObjectWrapper searchByCriteria(String sql);
}
