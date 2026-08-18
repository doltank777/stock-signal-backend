package com.stockapp.domain.user;

import com.stockapp.global.security.JwtTokenProvider;
import com.stockapp.global.util.CryptoUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CryptoUtil cryptoUtil;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void registersNewUserWithFreeMembership() {
        RegisterRequest request = mock(RegisterRequest.class);
        when(request.getEmail()).thenReturn("user@example.com");
        when(request.getPassword()).thenReturn("password");
        when(request.getNickname()).thenReturn("user");
        when(request.getPhoneNumber()).thenReturn("01012345678");
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(cryptoUtil.encrypt("01012345678")).thenReturn("encrypted-phone");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getMembershipType()).isEqualTo(MembershipType.FREE);
        assertThat(savedUser.getMembershipStartedAt()).isNull();
        assertThat(savedUser.getMembershipExpiredAt()).isNull();
    }
}
