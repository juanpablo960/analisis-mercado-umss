package com.umss.sistemas.tallerss.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "empleadores")
@Data
public class Empleador {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_encuesta")
    private LocalDateTime fechaEncuesta;

    @Column(name = "nombre_empresa")
    private String nombreEmpresa;

    // Guardaremos todas las respuestas en JSON
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> respuestas;
}