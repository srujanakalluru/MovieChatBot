package com.chatbot.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoogleTokenVerifier {

  private final GoogleIdTokenVerifier verifier;

  public GoogleTokenVerifier(@Value("${security.google.client-id}") String clientId) {
    this.verifier =
        new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(List.of(clientId))
            .build();
  }

  public GoogleIdToken.Payload verify(String idTokenString) {
    try {
      GoogleIdToken idToken = verifier.verify(idTokenString);
      if (idToken == null) {
        throw new IllegalArgumentException("Invalid Google ID token");
      }
      return idToken.getPayload();
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Google ID token verification failed", e);
    }
  }
}
