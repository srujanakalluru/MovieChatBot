package com.chatbot.beans;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class Genre {
  @Id private Integer id;
  private String name;
}
