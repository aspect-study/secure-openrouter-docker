package com.openrouter.gateway.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {

    Optional<ModelConfig> findByModelId(String modelId);

    List<ModelConfig> findAllByOrderByModelIdAsc();
}
