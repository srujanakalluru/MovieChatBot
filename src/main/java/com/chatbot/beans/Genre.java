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
@AllArgsConstructor
@NoArgsConstructor
public class Genre {
  @Id private Integer id;
  private String name;
}
