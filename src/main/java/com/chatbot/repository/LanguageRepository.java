/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.repository;

import com.chatbot.beans.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LanguageRepository extends JpaRepository<Language, Long> {}
