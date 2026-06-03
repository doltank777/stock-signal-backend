package com.stockapp.domain.favorite;

import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import com.stockapp.domain.user.User;
import com.stockapp.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;

    public List<FavoriteResponse> getFavorites(String email) {
        User user = getUser(email);

        return favoriteRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(FavoriteResponse::from)
                .toList();
    }

    public void addFavorite(String email, String stockCode) {
        User user = getUser(email);
        Stock stock = getStock(stockCode);

        if (favoriteRepository.existsByUserAndStock(user, stock)) {
            return;
        }

        Favorite favorite = Favorite.builder()
                .user(user)
                .stock(stock)
                .build();

        favoriteRepository.save(favorite);
    }

    public void removeFavorite(String email, String stockCode) {
        User user = getUser(email);
        Stock stock = getStock(stockCode);

        favoriteRepository.findByUserAndStock(user, stock)
                .ifPresent(favoriteRepository::delete);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    private Stock getStock(String stockCode) {
        return stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("종목을 찾을 수 없습니다."));
    }
}