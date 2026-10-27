package com.umss.sistemas.tallerss.controller;

import com.umss.sistemas.tallerss.service.ExcelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*")
public class UploadController {

    @Autowired
    private ExcelService excelService;

    @PostMapping("/titulados")
    public ResponseEntity<String> uploadTitulados(@RequestParam("file") MultipartFile file) {
        try {
            excelService.procesarExcelTitulados(file);
            return ResponseEntity.ok("Archivo de titulados procesado correctamente.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/empleadores")
    public ResponseEntity<String> uploadEmpleadores(@RequestParam("file") MultipartFile file) {
        try {
            excelService.procesarExcelEmpleadores(file);
            return ResponseEntity.ok("Archivo de empleadores procesado correctamente.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}