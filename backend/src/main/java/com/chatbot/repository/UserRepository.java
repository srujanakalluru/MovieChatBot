package com.chatbot.repository;

import com.chatbot.beans.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByGoogleSub(String googleSub);
}
