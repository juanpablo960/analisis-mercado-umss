package com.umss.sistemas.tallerss.repository;

import com.umss.sistemas.tallerss.entity.Titulado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TituladoRepository extends JpaRepository<Titulado, Long> {
}