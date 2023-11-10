/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.beans;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Language {
  @Id private String iso_639_1;
  private String english_name;
  private String name;
}
