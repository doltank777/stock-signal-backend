package com.stockapp.domain.signal;

import com.stockapp.domain.signal.dto.SignalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SignalService {

    private final SignalRepository signalRepository;

    public List<SignalResponse> getSignals() {
        return signalRepository.findAllWithStockOrderByDetectedAtDesc()
                .stream()
                .limit(50)
                .map(SignalResponse::from)
                .toList();
    }
}
