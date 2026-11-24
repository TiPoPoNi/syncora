package com.prs.controller;

import com.prs.dto.ClientDetailDto;
import com.prs.dto.ClientDetailDto.ClientProjectSummaryDto;
import com.prs.dto.ClientDto;
import com.prs.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor

public class ClientController {
    private final ClientService clientService;

    @GetMapping
    public List<ClientDto> getAllClients() {
        return clientService.getAllClients();
    }

    @GetMapping("/{id}")
    public ClientDto getClientById(@PathVariable Long id) {
        return clientService.getClientById(id);
    }

    @GetMapping("/{id}/details")
    public ClientDetailDto getClientDetails(@PathVariable Long id) {
        return clientService.getClientDetails(id);
    }

    @GetMapping("/{id}/projects")
    public List<ClientProjectSummaryDto> getClientProjects(@PathVariable Long id) {
        return clientService.getClientProjects(id);
    }

    @PostMapping
    public ClientDto createClient(@RequestBody ClientDto clientDto) {
        return clientService.createClient(clientDto);
    }

    @PutMapping("/{id}")
    public ClientDto updateClient(@PathVariable Long id, @RequestBody ClientDto clientDto) {
        return clientService.updateClient(id, clientDto);
    }

    @PutMapping("/{id}/archive")
    public ClientDto archiveClient(@PathVariable Long id) {
        return clientService.archiveClient(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClient(@PathVariable Long id) {
        clientService.deleteClient(id);
        return ResponseEntity.ok().build();
    }
}
