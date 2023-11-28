package com.chatbot.beans;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "iso_639_1")
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Language {
  @Id private String iso_639_1;
  private String english_name;
  private String name;
}
