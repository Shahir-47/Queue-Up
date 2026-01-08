package com.QueueUp.Backend.repository;

import com.QueueUp.Backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    // Finds all users whose ID is NOT in the provided list
    List<User> findByIdNotIn(List<Long> excludedIds);
}