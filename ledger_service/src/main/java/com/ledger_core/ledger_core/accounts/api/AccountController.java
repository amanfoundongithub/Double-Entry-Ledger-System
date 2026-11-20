package com.ledger_core.ledger_core.accounts.api;

import com.ledger_core.ledger_core.accounts.application.AccountService;
import com.ledger_core.ledger_core.accounts.domain.AccountType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse created = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAccounts(
            @RequestParam(required = false) AccountType type,
            @RequestParam(required = false) Boolean activeOnly
    ) {
        return ResponseEntity.ok(accountService.listAccounts(type, activeOnly));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(accountService.getById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<AccountResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(accountService.getByCode(code));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<AccountResponse> deactivateAccount(@PathVariable String id) {
        return ResponseEntity.ok(accountService.deactivateAccount(id));
    }
}