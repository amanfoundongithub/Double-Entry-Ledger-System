package com.ledger_core.ledger_core.accounts.application;

import com.ledger_core.ledger_core.accounts.api.AccountResponse;
import com.ledger_core.ledger_core.accounts.api.CreateAccountRequest;
import com.ledger_core.ledger_core.accounts.domain.Account;
import com.ledger_core.ledger_core.accounts.domain.AccountType;
import com.ledger_core.ledger_core.accounts.infrastructure.AccountRepository;
import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private final Instant now = Instant.parse("2026-11-20T17:15:00Z");
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                accountRepository,
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("Creates account with normalized code and domain defaults")
    void createsNormalizedAccount() {
        when(accountRepository.existsByCode("1010")).thenReturn(false);
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        CreateAccountRequest request = new CreateAccountRequest(
                " 1010 ",
                "Operating bank",
                AccountType.ASSET,
                null,
                null,
                null,
                null,
                null,
                null
        );

        AccountResponse response = accountService.createAccount(request);

        assertEquals("1010", response.code());
        assertEquals("Operating bank", response.name());
        assertEquals(AccountType.ASSET, response.type());
        assertEquals("DEBIT", response.normalSide().name());
        assertEquals("INR", response.currency());
        assertTrue(response.active());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());
        verify(accountRepository).save(argThat(account ->
                account.getCode().equals("1010")
                        && account.getCreatedAt().equals(now)
        ));
    }

    @Test
    @DisplayName("Rejects duplicate account codes without saving")
    void rejectsDuplicateCode() {
        when(accountRepository.existsByCode("1010")).thenReturn(true);
        CreateAccountRequest request = new CreateAccountRequest(
                " 1010 ",
                "Operating bank",
                AccountType.ASSET,
                null,
                null,
                null,
                null,
                null,
                null
        );

        DomainException exception = assertThrows(
                DomainException.class,
                () -> accountService.createAccount(request)
        );

        assertTrue(exception.getMessage().contains("1010"));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Selects active and typed account query when both filters are requested")
    void listsActiveAccountsByType() {
        when(accountRepository.findByTypeAndActiveTrueOrderByCodeAsc(AccountType.ASSET))
                .thenReturn(List.of());

        assertTrue(accountService.listAccounts(AccountType.ASSET, true).isEmpty());

        verify(accountRepository).findByTypeAndActiveTrueOrderByCodeAsc(AccountType.ASSET);
        verifyNoMoreInteractions(accountRepository);
    }
}
