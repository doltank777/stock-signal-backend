package com.stockapp.domain.favorite;

import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUserOrderByCreatedAtDesc(User user);

    Optional<Favorite> findByUserAndStock(User user, Stock stock);

    boolean existsByUserAndStock(User user, Stock stock);
}