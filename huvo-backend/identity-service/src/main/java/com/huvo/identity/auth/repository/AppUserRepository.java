package com.huvo.identity.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.huvo.identity.auth.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

  Optional<AppUser> findByUsername(String username);
}
