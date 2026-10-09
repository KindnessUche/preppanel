package com.preppanel.profile;

import com.preppanel.profile.dto.ProfileResponse;
import com.preppanel.profile.dto.UpdatePortfolioRequest;
import com.preppanel.security.AppUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping(value = "/resume", consumes = "multipart/form-data")
    public ResponseEntity<ProfileResponse> uploadResume(Authentication auth,
                                                          @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(profileService.uploadResume(userId(auth), file));
    }

    @PutMapping("/portfolio")
    public ResponseEntity<ProfileResponse> updatePortfolio(Authentication auth,
                                                             @Valid @RequestBody UpdatePortfolioRequest request) {
        return ResponseEntity.ok(profileService.updatePortfolio(userId(auth), request));
    }

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(Authentication auth) {
        return ResponseEntity.ok(profileService.getProfile(userId(auth)));
    }

    private UUID userId(Authentication auth) {
        return ((AppUserPrincipal) auth.getPrincipal()).getId();
    }
}
