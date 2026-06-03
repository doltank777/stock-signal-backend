package com.stockapp.domain.favorite;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public List<FavoriteResponse> getFavorites(Authentication authentication) {
        return favoriteService.getFavorites(authentication.getName());
    }

    @PostMapping("/{stockCode}")
    public void addFavorite(
            Authentication authentication,
            @PathVariable String stockCode
    ) {
        favoriteService.addFavorite(authentication.getName(), stockCode);
    }

    @DeleteMapping("/{stockCode}")
    public void removeFavorite(
            Authentication authentication,
            @PathVariable String stockCode
    ) {
        favoriteService.removeFavorite(authentication.getName(), stockCode);
    }
}