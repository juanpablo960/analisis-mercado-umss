package com.umss.sistemas.tallerss.repository;

import com.umss.sistemas.tallerss.entity.Empleador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmpleadorRepository extends JpaRepository<Empleador, Long> {
}