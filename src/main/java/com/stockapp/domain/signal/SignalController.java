package com.stockapp.domain.signal;

import com.stockapp.domain.signal.dto.SignalResponse;
import com.stockapp.domain.signal.SignalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalController {

    private final SignalService signalService;

    @GetMapping
    public List<SignalResponse> getSignals() {
        return signalService.getSignals();
    }
}