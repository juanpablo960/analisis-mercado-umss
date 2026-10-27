package com.umss.sistemas.tallerss.service;

import com.umss.sistemas.tallerss.entity.Empleador;
import com.umss.sistemas.tallerss.entity.Titulado;
import com.umss.sistemas.tallerss.repository.EmpleadorRepository;
import com.umss.sistemas.tallerss.repository.TituladoRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ExcelService {

    @Autowired
    private TituladoRepository tituladoRepository;

    @Autowired
    private EmpleadorRepository empleadorRepository;

    public void procesarExcelTitulados(MultipartFile file) throws Exception {
        List<Titulado> lista = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                Titulado t = new Titulado();
                t.setFechaEncuesta(LocalDateTime.now());
                Map<String, Object> respuestas = new HashMap<>();
                
                for (int j = 0; j < headerRow.getLastCellNum(); j++) {
                    Cell cell = row.getCell(j);
                    String header = headerRow.getCell(j) != null ? headerRow.getCell(j).toString() : "Col_" + j;
                    respuestas.put(header, getCellValue(cell));
                }
                t.setRespuestas(respuestas);
                lista.add(t);
            }
        }
        tituladoRepository.saveAll(lista);
    }

    public void procesarExcelEmpleadores(MultipartFile file) throws Exception {
        List<Empleador> lista = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                Empleador e = new Empleador();
                e.setFechaEncuesta(LocalDateTime.now());
                Map<String, Object> respuestas = new HashMap<>();
                
                for (int j = 0; j < headerRow.getLastCellNum(); j++) {
                    Cell cell = row.getCell(j);
                    String header = headerRow.getCell(j) != null ? headerRow.getCell(j).toString() : "Col_" + j;
                    respuestas.put(header, getCellValue(cell));
                    
                    if (header.contains("Nombre de la Empresa")) {
                        e.setNombreEmpresa(getCellValue(cell));
                    }
                }
                e.setRespuestas(respuestas);
                lista.add(e);
            }
        }
        empleadorRepository.saveAll(lista);
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }
}