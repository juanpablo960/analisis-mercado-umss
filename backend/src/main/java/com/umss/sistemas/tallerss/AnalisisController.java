package com.umss.sistemas.tallerss.analisis;



import com.umss.sistemas.tallerss.entity.Empleador;
import com.umss.sistemas.tallerss.entity.Titulado;
import com.umss.sistemas.tallerss.repository.EmpleadorRepository;
import com.umss.sistemas.tallerss.repository.TituladoRepository;
import com.umss.sistemas.tallerss.service.SimulacionService;

import jakarta.servlet.http.HttpServletResponse; // <-- ESTA ES LA QUE FALTA
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.*;
@RestController
@RequestMapping("/api/analisis")
@CrossOrigin(origins = "*")
public class AnalisisController {

    @Autowired
    private TituladoRepository tituladoRepository;

    @Autowired
    private EmpleadorRepository empleadorRepository;

    @Autowired
    private SimulacionService simulacionService;

    // 1. Estadística Descriptiva
    @GetMapping("/descriptiva")
    public Map<String, Object> getDescriptiva() {
        List<Titulado> titulados = tituladoRepository.findAll();
        List<Double> aniosProfesionales = new ArrayList<>();
        
        for (Titulado t : titulados) {
            try {
                String valorStr = (String) t.getRespuestas().entrySet().stream()
                    .filter(e -> e.getKey().toUpperCase().contains("AÑOS DE VIDA PROFESIONAL"))
                    .map(Map.Entry::getValue)
                    .findFirst().orElse("0");
                
                valorStr = valorStr.replaceAll("[^0-9.]", "");
                if (!valorStr.isEmpty()) {
                    aniosProfesionales.add(Double.parseDouble(valorStr));
                }
            } catch (Exception e) {}
        }

        Map<String, Object> resultado = new HashMap<>();
        if (!aniosProfesionales.isEmpty()) {
            DoubleSummaryStatistics stats = aniosProfesionales.stream().mapToDouble(Double::doubleValue).summaryStatistics();
            resultado.put("media_anios", stats.getAverage());
            resultado.put("min_anios", stats.getMin());
            resultado.put("max_anios", stats.getMax());
            resultado.put("total_registros", stats.getCount());
        }
        return resultado;
    }

    // 2. Áreas de Interés
    @GetMapping("/areas-interes")
    public Map<String, Long> getAreasInteres() {
        List<Titulado> titulados = tituladoRepository.findAll();
        Map<String, Long> conteo = new HashMap<>();

        for (Titulado t : titulados) {
            String area = (String) t.getRespuestas().entrySet().stream()
                .filter(e -> e.getKey().toUpperCase().contains("EN CUÁL DE LAS SIGUIENTES ÁREAS"))
                .map(Map.Entry::getValue)
                .findFirst().orElse("No especificado");
            
            if (area != null && !area.trim().isEmpty()) {
                conteo.put(area, conteo.getOrDefault(area, 0L) + 1);
            }
        }
        return conteo;
    }

    // 3. Simulación Empleabilidad
    @GetMapping("/simular-empleabilidad")
    public Map<String, Object> simularEmpleabilidad(@RequestParam(defaultValue = "1000") int corridas) {
        return simulacionService.simularEmpleabilidad(corridas);
    }

    // 4. Nivel de Formación Empleadores
    @GetMapping("/empleadores/nivel-formacion")
    public Map<String, Long> getNivelFormacionEmpleadores() {
        List<Empleador> empleadores = empleadorRepository.findAll();
        Map<String, Long> conteo = new HashMap<>();

        for (Empleador e : empleadores) {
            String nivel = (String) e.getRespuestas().entrySet().stream()
                .filter(entry -> entry.getKey().toUpperCase().contains("NIVEL DE FORMACIÓN ACADÉMICA O ACTUALIZACIÓN"))
                .map(Map.Entry::getValue).findFirst().orElse("No especificado");
            
            if (nivel != null && !nivel.trim().isEmpty()) {
                conteo.put(nivel, conteo.getOrDefault(nivel, 0L) + 1);
            }
        }
        return conteo;
    }

    // 5. Prueba Chi-Cuadrado
        @GetMapping("/chi-cuadrado-dinamico")
    public Map<String, Object> calcularChiCuadradoDinamico(
            @RequestParam(defaultValue = "GÉNERO") String variable1,
            @RequestParam(defaultValue = "EN CUÁL DE LAS SIGUIENTES ÁREAS") String variable2) {

        List<Titulado> titulados = tituladoRepository.findAll();
        
        // 1. Identificar valores únicos
        Set<String> filas = new TreeSet<>();
        Set<String> columnas = new TreeSet<>();
        for (Titulado t : titulados) {
            String v1 = getValor(t, variable1);
            String v2 = getValor(t, variable2);
            if (!v1.equals("N/A") && !v2.equals("N/A")) {
                filas.add(v1);
                columnas.add(v2);
            }
        }

        // 2. Crear Matriz de Frecuencias Observadas (FO)
        Map<String, Map<String, Integer>> matrizFO = new LinkedHashMap<>();
        Map<String, Integer> totalesFila = new LinkedHashMap<>();
        Map<String, Integer> totalesColumna = new LinkedHashMap<>();
        int totalGeneral = 0;

        for (String f : filas) {
            matrizFO.put(f, new LinkedHashMap<>());
            totalesFila.put(f, 0);
        }
        for (String c : columnas) {
            totalesColumna.put(c, 0);
        }

        for (Titulado t : titulados) {
            String v1 = getValor(t, variable1);
            String v2 = getValor(t, variable2);
            if (filas.contains(v1) && columnas.contains(v2)) {
                matrizFO.get(v1).merge(v2, 1, Integer::sum);
                totalesFila.merge(v1, 1, Integer::sum);
                totalesColumna.merge(v2, 1, Integer::sum);
                totalGeneral++;
            }
        }

        // 3. Crear Matriz de Frecuencias Esperadas (FE) y Calcular Chi-Cuadrado
        Map<String, Map<String, Double>> matrizFE = new LinkedHashMap<>();
        double chi = 0.0;

        for (String f : filas) {
            matrizFE.put(f, new LinkedHashMap<>());
            for (String c : columnas) {
                int fo = matrizFO.get(f).getOrDefault(c, 0);
                int tf = totalesFila.get(f);
                int tc = totalesColumna.get(c);
                double fe = (double) (tf * tc) / totalGeneral;
                
                matrizFE.get(f).put(c, Math.round(fe * 100.0) / 100.0);
                
                if (fe > 0) {
                    chi += Math.pow(fo - fe, 2) / fe;
                }
            }
        }

        int gl = (filas.size() - 1) * (columnas.size() - 1);
        // Valor crítico aproximado según grados de libertad (tabla Chi-Cuadrado)
        double critico = 0;
        if (gl == 1) critico = 3.841;
        else if (gl == 2) critico = 5.991;
        else if (gl == 3) critico = 7.815;
        else if (gl == 4) critico = 9.488;
        else if (gl == 5) critico = 11.070;
        else if (gl == 6) critico = 12.592;
        else if (gl == 8) critico = 15.507;
        else if (gl == 10) critico = 18.307;
        else critico = 20.0; // Fallback

        boolean independiente = chi < critico;

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("filas", filas);
        resultado.put("columnas", columnas);
        resultado.put("matriz_fo", matrizFO);
        resultado.put("matriz_fe", matrizFE);
        resultado.put("totales_fila", totalesFila);
        resultado.put("totales_columna", totalesColumna);
        resultado.put("total_general", totalGeneral);
        resultado.put("chi_calculado", Math.round(chi * 100.0) / 100.0);
        resultado.put("grados_libertad", gl);
        resultado.put("valor_critico", critico);
        resultado.put("es_independiente", independiente);
        resultado.put("conclusion", independiente ? 
            "Las variables son INDEPENDIENTES (no hay relación significativa)" : 
            "Las variables NO son independientes (existe relación significativa)");

        return resultado;
    }
    // 6. Simulación de Demanda Académica
    @GetMapping("/simular-demanda-academica")
    public Map<String, Object> simularDemandaAcademica(@RequestParam(defaultValue = "1000") int corridas) {
        int interesadosIA = 0;
        double costoMaestria = 15000;

        for (int i = 0; i < corridas; i++) {
            double r = simulacionService.generarAleatorio(); 
            if (r < 0.45) {
                interesadosIA++;
            }
        }

        double promedioAlumnos = (double) interesadosIA / corridas * 100;
        double ingresoEstimado = promedioAlumnos * costoMaestria;

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("corridas", corridas);
        resultado.put("porcentaje_interes", Math.round((promedioAlumnos / 100) * 10000.0) / 100.0);
        resultado.put("alumnos_estimados_por_100", Math.round(promedioAlumnos * 100.0) / 100.0);
        resultado.put("ingreso_estimado_bs", Math.round(ingresoEstimado * 100.0) / 100.0);
        
        return resultado;
    }

        // 7. Promedio de Satisfacción (Escala Likert)
    @GetMapping("/satisfaccion-promedio")
    public Map<String, Object> getSatisfaccionPromedio() {
        List<Titulado> titulados = tituladoRepository.findAll();
        List<Double> valores = new ArrayList<>();

        for (Titulado t : titulados) {
            String valorStr = (String) t.getRespuestas().entrySet().stream()
                .filter(e -> e.getKey().toUpperCase().contains("GRADO DE SATISFACCIÓN QUE HA DESARROLLADO CON LA FORMACIÓN PROFESIONAL"))
                .map(Map.Entry::getValue).findFirst().orElse("");
            
            // Convertimos la escala de texto a número (1 a 4)
            if (valorStr != null) {
                if (valorStr.contains("Totalmente en desacuerdo")) valores.add(1.0);
                else if (valorStr.contains("En desacuerdo")) valores.add(2.0);
                else if (valorStr.contains("De acuerdo")) valores.add(3.0);
                else if (valorStr.contains("Totalmente de Acuerdo")) valores.add(4.0);
            }
        }

        Map<String, Object> resultado = new HashMap<>();
        if (!valores.isEmpty()) {
            double promedio = valores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            resultado.put("promedio", Math.round(promedio * 100.0) / 100.0);
            resultado.put("total_encuestados", valores.size());
        } else {
            resultado.put("promedio", 0);
        }
        return resultado;
    }
        // 8. Contador Total de Registros (Para la pantalla de Inicio)
    @GetMapping("/resumen-totales")
    public Map<String, Long> getResumenTotales() {
        Map<String, Long> totales = new HashMap<>();
        totales.put("total_titulados", tituladoRepository.count());
        totales.put("total_empleadores", empleadorRepository.count());
        return totales;
    }

        // 9. Lista de Titulados (Para la tabla del Frontend)
    @GetMapping("/lista-titulados")
    public List<Map<String, Object>> getListaTitulados() {
        List<Titulado> titulados = tituladoRepository.findAll();
        List<Map<String, Object>> listaResumida = new ArrayList<>();

        for (Titulado t : titulados) {
            Map<String, Object> fila = new HashMap<>();
            fila.put("id", t.getId());

            // Extraer datos del JSON
            String edad = getValor(t, "EDAD");
            String genero = getValor(t, "GÉNERO");
            String anioEgreso = getValor(t, "AÑO DE EGRESO");
            String area = getValor(t, "EN CUÁL DE LAS SIGUIENTES ÁREAS");
            String situacion = getValor(t, "SITUACIÓN LABORAL ACTUAL");

            // Simplificar textos largos para la tabla
            if (situacion.contains("Actualmente me encuentro trabajando")) situacion = "Empleado";
            if (situacion.contains("Actualmente NO trabajo")) situacion = "Desempleado";
            if (situacion.contains("emprendimiento propio")) situacion = "Emprendedor";

            fila.put("edad", edad);
            fila.put("genero", genero);
            fila.put("anioEgreso", anioEgreso);
            fila.put("area", area);
            fila.put("situacion", situacion);

            listaResumida.add(fila);
        }
        return listaResumida;
    }

    // Método auxiliar para buscar un valor en el JSON por palabra clave
    private String getValor(Titulado t, String palabraClave) {
        if (t.getRespuestas() == null) return "N/A";
        return t.getRespuestas().entrySet().stream()
            .filter(e -> e.getKey().toUpperCase().contains(palabraClave.toUpperCase()))
            .map(e -> e.getValue().toString())
            .findFirst()
            .orElse("N/A");
    }

        // 10. Obtener detalles de un Titulado por ID
    @GetMapping("/titulado/{id}")
    public Map<String, Object> getTituladoDetalle(@PathVariable Long id) {
        Titulado t = tituladoRepository.findById(id).orElse(null);
        if (t == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Titulado no encontrado");
            return error;
        }

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("id", t.getId());
        resultado.put("fecha", t.getFechaEncuesta());
        
        // Devolvemos todo el mapa de respuestas tal cual está en el JSONB
        resultado.put("respuestas", t.getRespuestas());
        
        return resultado;
    }

        // 11. Lista de Empleadores (Resumida para la tabla)
    @GetMapping("/lista-empleadores")
    public List<Map<String, Object>> getListaEmpleadores() {
        List<Empleador> empleadores = empleadorRepository.findAll();
        List<Map<String, Object>> listaResumida = new ArrayList<>();

        for (Empleador e : empleadores) {
            Map<String, Object> fila = new HashMap<>();
            fila.put("id", e.getId());
            fila.put("nombre", e.getNombreEmpresa() != null ? e.getNombreEmpresa() : "N/A");

            // Extraer datos clave del JSON
            String tipo = getValorEmpleador(e, "TIPO DE ORGANIZACIÓN");
            String tamano = getValorEmpleador(e, "TAMAÑO DE LA ORGANIZACIÓN");
            String rubro = getValorEmpleador(e, "RUBRO O SECTOR PRINCIPAL");
            String nivel = getValorEmpleador(e, "NIVEL DE FORMACIÓN");

            // Limpiar textos largos para la tabla
            if (rubro.length() > 40) rubro = rubro.substring(0, 40) + "...";
            if (tamano.contains("(")) tamano = tamano.split("\\(")[0].trim();

            fila.put("tipo", tipo);
            fila.put("tamano", tamano);
            fila.put("rubro", rubro);
            fila.put("nivel", nivel);

            listaResumida.add(fila);
        }
        return listaResumida;
    }

    // 12. Detalle de un Empleador por ID
    @GetMapping("/empleador/{id}")
    public Map<String, Object> getEmpleadorDetalle(@PathVariable Long id) {
        Empleador e = empleadorRepository.findById(id).orElse(null);
        if (e == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Empleador no encontrado");
            return error;
        }

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("id", e.getId());
        resultado.put("fecha", e.getFechaEncuesta());
        resultado.put("nombre", e.getNombreEmpresa());
        resultado.put("respuestas", e.getRespuestas());
        
        return resultado;
    }

    // Método auxiliar para buscar en el JSON del Empleador
    private String getValorEmpleador(Empleador e, String palabraClave) {
        if (e.getRespuestas() == null) return "N/A";
        return e.getRespuestas().entrySet().stream()
            .filter(entry -> entry.getKey().toUpperCase().contains(palabraClave.toUpperCase()))
            .map(entry -> entry.getValue().toString())
            .findFirst()
            .orElse("N/A");
    }

        // 13. Reportes Dinámicos (Con Filtros)
    @GetMapping("/reportes-dinamicos")
    public Map<String, Object> getReportesDinamicos(
            @RequestParam(required = false, defaultValue = "") String genero,
            @RequestParam(required = false, defaultValue = "") String area) {

        List<Titulado> titulados = tituladoRepository.findAll();
        
        // Estructuras para los resultados
        Map<String, Long> porGenero = new HashMap<>();
        Map<String, Long> porArea = new HashMap<>();
        Map<String, Long> porSituacion = new HashMap<>();
        Map<String, Long> porFormacion = new HashMap<>();
        Map<String, Long> porTiempoEmpleo = new HashMap<>();
        Map<String, Long> porNivelAlcanzado = new HashMap<>();
        
        int totalFiltrados = 0;

        for (Titulado t : titulados) {
            String g = getValor(t, "GÉNERO");
            String a = getValor(t, "EN CUÁL DE LAS SIGUIENTES ÁREAS");
            
            // Lógica de filtrado
            boolean cumpleGenero = genero.isEmpty() || g.equalsIgnoreCase(genero);
            boolean cumpleArea = area.isEmpty() || a.equalsIgnoreCase(area);
            
            if (cumpleGenero && cumpleArea) {
                totalFiltrados++;
                
                // Acumular datos para los gráficos
                porGenero.merge(g, 1L, Long::sum);
                porArea.merge(a, 1L, Long::sum);
                
                String situacion = getValor(t, "SITUACIÓN LABORAL ACTUAL");
                if (situacion.contains("trabajando")) situacion = "Empleado";
                else if (situacion.contains("NO trabajo")) situacion = "Desempleado";
                else if (situacion.contains("emprendimiento")) situacion = "Emprendedor";
                porSituacion.merge(situacion, 1L, Long::sum);
                
                porFormacion.merge(getValor(t, "HA REALIZADO O SE ENCUENTRA"), 1L, Long::sum);
                porTiempoEmpleo.merge(getValor(t, "CUÁNTO TIEMPO SE DEMORÓ"), 1L, Long::sum);
                porNivelAlcanzado.merge(getValor(t, "PROGRAMA DE FORMACIÓN COMPLEMENTARIA"), 1L, Long::sum);
            }
        }

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("total_filtrados", totalFiltrados);
        resultado.put("por_genero", porGenero);
        resultado.put("por_area", porArea);
        resultado.put("por_situacion", porSituacion);
        resultado.put("por_formacion", porFormacion);
        resultado.put("por_tiempo_empleo", porTiempoEmpleo);
        resultado.put("por_nivel_alcanzado", porNivelAlcanzado);

        return resultado;
    }

        // 14. Lista Filtrada por Categoría (Para el botón "Ver" de las tablas)
    @GetMapping("/lista-filtrada")
    public List<Map<String, Object>> getListaFiltrada(
            @RequestParam String categoria, // Ej: "GÉNERO", "SITUACIÓN LABORAL", "EN CUÁL..."
            @RequestParam String valor,     // Ej: "Mujer", "IA", "Empleado"
            @RequestParam(required = false, defaultValue = "") String filtroGenero,
            @RequestParam(required = false, defaultValue = "") String filtroArea) {

        List<Titulado> titulados = tituladoRepository.findAll();
        List<Map<String, Object>> listaFiltrada = new ArrayList<>();

        for (Titulado t : titulados) {
            String generoActual = getValor(t, "GÉNERO");
            String areaActual = getValor(t, "EN CUÁL DE LAS SIGUIENTES ÁREAS");

            // 1. Aplicar filtros globales (los del panel superior)
            boolean cumpleGeneroGlobal = filtroGenero.isEmpty() || generoActual.equalsIgnoreCase(filtroGenero);
            boolean cumpleAreaGlobal = filtroArea.isEmpty() || areaActual.equalsIgnoreCase(filtroArea);

            if (cumpleGeneroGlobal && cumpleAreaGlobal) {
                // 2. Aplicar el filtro específico de la tabla (ej. buscar a los que son "Mujer")
                String valorActual = getValor(t, categoria);
                
                // Comparación especial para la situación laboral que tiene textos largos
                boolean coincide = false;
                if (categoria.contains("SITUACIÓN")) {
                    if (valor.equals("Empleado") && valorActual.contains("trabajando")) coincide = true;
                    else if (valor.equals("Desempleado") && valorActual.contains("NO trabajo")) coincide = true;
                    else if (valor.equals("Emprendedor") && valorActual.contains("emprendimiento")) coincide = true;
                } else {
                    coincide = valorActual.equalsIgnoreCase(valor);
                }

                if (coincide) {
                    Map<String, Object> fila = new HashMap<>();
                    fila.put("id", t.getId());
                    fila.put("edad", generoActual); // Reutilizamos variables o extraemos más
                    fila.put("genero", generoActual);
                    fila.put("area", areaActual);
                    fila.put("situacion", getValor(t, "SITUACIÓN LABORAL ACTUAL"));
                    listaFiltrada.add(fila);
                }
            }
        }
        return listaFiltrada;
    }

        // 15. Reportes Dinámicos para Empleadores
    @GetMapping("/reportes-empleadores")
    public Map<String, Object> getReportesEmpleadores(
            @RequestParam(required = false, defaultValue = "") String tipo,
            @RequestParam(required = false, defaultValue = "") String tamano) {

        List<Empleador> empleadores = empleadorRepository.findAll();
        
        Map<String, Long> porTipo = new HashMap<>();
        Map<String, Long> porTamano = new HashMap<>();
        Map<String, Long> porRubro = new HashMap<>();
        Map<String, Long> porNivel = new HashMap<>();
        Map<String, Long> porCargos = new HashMap<>();
        
        int totalFiltrados = 0;

        for (Empleador e : empleadores) {
            String t = getValorEmpleador(e, "TIPO DE ORGANIZACIÓN");
            String tm = getValorEmpleador(e, "TAMAÑO DE LA ORGANIZACIÓN");
            
            // Lógica de filtrado
            boolean cumpleTipo = tipo.isEmpty() || t.equalsIgnoreCase(tipo);
            // El tamaño viene con texto largo (ej. "Grande (100 funcionarios o más)"), lo simplificamos
            String tamanoSimplificado = tm.split("\\(")[0].trim();
            boolean cumpleTamano = tamano.isEmpty() || tamanoSimplificado.equalsIgnoreCase(tamano);
            
            if (cumpleTipo && cumpleTamano) {
                totalFiltrados++;
                
                porTipo.merge(t, 1L, Long::sum);
                porTamano.merge(tamanoSimplificado, 1L, Long::sum);
                
                String rubro = getValorEmpleador(e, "RUBRO O SECTOR PRINCIPAL");
                porRubro.merge(rubro, 1L, Long::sum);
                
                porNivel.merge(getValorEmpleador(e, "NIVEL DE FORMACIÓN ACADÉMICA"), 1L, Long::sum);
                
                // Para cargos, a veces vienen varios separados por coma. Tomamos el primero para simplificar.
                String cargos = getValorEmpleador(e, "QUÉ TIPO DE CARGOS");
                String primerCargo = cargos.contains(",") ? cargos.split(",")[0].trim() : cargos;
                porCargos.merge(primerCargo, 1L, Long::sum);
            }
        }

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("total_filtrados", totalFiltrados);
        resultado.put("por_tipo", porTipo);
        resultado.put("por_tamano", porTamano);
        resultado.put("por_rubro", porRubro);
        resultado.put("por_nivel", porNivel);
        resultado.put("por_cargos", porCargos);

        return resultado;
    }

    // 16. Lista Filtrada de Empleadores (Para el botón "Ver")
    @GetMapping("/lista-filtrada-empleadores")
    public List<Map<String, Object>> getListaFiltradaEmpleadores(
            @RequestParam String categoria,
            @RequestParam String valor,
            @RequestParam(required = false, defaultValue = "") String filtroTipo,
            @RequestParam(required = false, defaultValue = "") String filtroTamano) {

        List<Empleador> empleadores = empleadorRepository.findAll();
        List<Map<String, Object>> listaFiltrada = new ArrayList<>();

        for (Empleador e : empleadores) {
            String tipoActual = getValorEmpleador(e, "TIPO DE ORGANIZACIÓN");
            String tamanoActual = getValorEmpleador(e, "TAMAÑO DE LA ORGANIZACIÓN").split("\\(")[0].trim();

            boolean cumpleTipoGlobal = filtroTipo.isEmpty() || tipoActual.equalsIgnoreCase(filtroTipo);
            boolean cumpleTamanoGlobal = filtroTamano.isEmpty() || tamanoActual.equalsIgnoreCase(filtroTamano);

            if (cumpleTipoGlobal && cumpleTamanoGlobal) {
                String valorActual = getValorEmpleador(e, categoria);
                
                // Comparaciones especiales para campos que tienen texto largo
                boolean coincide = false;
                if (categoria.contains("TAMAÑO")) {
                    coincide = valorActual.split("\\(")[0].trim().equalsIgnoreCase(valor);
                } else if (categoria.contains("CARGOS")) {
                    coincide = valorActual.toUpperCase().contains(valor.toUpperCase());
                } else {
                    coincide = valorActual.equalsIgnoreCase(valor);
                }

                if (coincide) {
                    Map<String, Object> fila = new HashMap<>();
                    fila.put("id", e.getId());
                    fila.put("nombre", e.getNombreEmpresa() != null ? e.getNombreEmpresa() : "N/A");
                    fila.put("tipo", tipoActual);
                    fila.put("tamano", tamanoActual);
                    fila.put("rubro", getValorEmpleador(e, "RUBRO O SECTOR PRINCIPAL"));
                    fila.put("nivel", getValorEmpleador(e, "NIVEL DE FORMACIÓN ACADÉMICA"));
                    listaFiltrada.add(fila);
                }
            }
        }
        return listaFiltrada;
    }
        // 17. Simulación de Monte Carlo (Demanda de Posgrados)
    @GetMapping("/monte-carlo-demanda")
    public Map<String, Object> simularMonteCarloDemanda(@RequestParam(defaultValue = "1000") int simulaciones) {
        List<Titulado> titulados = tituladoRepository.findAll();
        int totalTitulados = titulados.size();
        
        // 1. Calcular la probabilidad real de interés en Posgrado
        long interesados = 0;
        for (Titulado t : titulados) {
            String resp = getValor(t, "ESTARÍA INTERESADO");
            if (resp.toUpperCase().contains("SI")) interesados++;
        }
        double probInteres = totalTitulados > 0 ? (double) interesados / totalTitulados : 0.0;
        
        // Media y Desviación Estándar teóricas (para la distribución normal)
        double mediaEsperada = totalTitulados * probInteres;
        // Asumimos una desviación estándar del 10% de la media para generar la campana
        double desviacionEsperada = mediaEsperada * 0.10; 

        long a = 1103515245;
        long c = 12345;
        long m = (long) Math.pow(2, 31); 
        long semilla = 12345;
        
        List<Double> aleatorios = new ArrayList<>();
        List<Double> demandas = new ArrayList<>();
        double sumaDemandas = 0;

        for (int i = 0; i < simulaciones; i++) {
            // Generar 12 números aleatorios y sumarlos (Teorema del Límite Central - Coss Bu 4.28)
            double sumaR = 0;
            for (int j = 0; j < 12; j++) {
                semilla = (a * semilla + c) % m;
                sumaR += (double) semilla / m;
            }
            
            // Transformada Inversa para Distribución Normal: x = media + desviacion * (sumaR - 6)
            double demandaSimulada = mediaEsperada + desviacionEsperada * (sumaR - 6);
            
            // Redondeamos y evitamos valores negativos o mayores al total
            demandaSimulada = Math.max(0, Math.min(totalTitulados, Math.round(demandaSimulada)));
            
            demandas.add(demandaSimulada);
            sumaDemandas += demandaSimulada;
            
            if (i < 20) {
                aleatorios.add(Math.round(((double) semilla / m) * 1000.0) / 1000.0);
            }
        }

        // 3. Calcular estadísticas
        double media = sumaDemandas / simulaciones;
        double varianza = 0;
        for (double d : demandas) varianza += Math.pow(d - media, 2);
        double desviacion = Math.sqrt(varianza / (simulaciones - 1));
        
        double errorEstandar = desviacion / Math.sqrt(simulaciones);
        double margenError = 1.96 * errorEstandar;
        
        // Demanda por área (simulada)
        Map<String, Long> demandaPorArea = new HashMap<>();
        demandaPorArea.put("IA", Math.round(media * 0.45));
        demandaPorArea.put("Ingeniería de Software", Math.round(media * 0.25));
        demandaPorArea.put("Base de Datos", Math.round(media * 0.15));

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("total_titulados", totalTitulados);
        resultado.put("probabilidad_interes", Math.round(probInteres * 10000.0) / 100.0);
        resultado.put("simulaciones", simulaciones);
        resultado.put("demanda_promedio", Math.round(media * 100.0) / 100.0);
        resultado.put("desviacion_estandar", Math.round(desviacion * 100.0) / 100.0);
        resultado.put("demanda_minima", Math.round(Collections.min(demandas)));
        resultado.put("demanda_maxima", Math.round(Collections.max(demandas)));
        resultado.put("ic_inferior", Math.round((media - margenError) * 100.0) / 100.0);
        resultado.put("ic_superior", Math.round((media + margenError) * 100.0) / 100.0);
        resultado.put("primeros_aleatorios", aleatorios);
        resultado.put("demanda_por_area", demandaPorArea);
        resultado.put("histograma_demandas", demandas);

        return resultado;
    }

        // 18. Simulación de Inversión - TIR (Tasa Interna de Retorno)
       @GetMapping("/simular-tir")
    public Map<String, Object> simularTIR(
            @RequestParam(defaultValue = "1000") int simulaciones,
            @RequestParam(defaultValue = "15") double trema,
            @RequestParam(defaultValue = "8000") double inversionBase,
            @RequestParam(defaultValue = "5000") double flujoBase) {

        long a = 1103515245;
        long c = 12345;
        long m = (long) Math.pow(2, 31);
        long semilla = 12345;

        List<Double> tirs = new ArrayList<>();
        List<Double> vpns = new ArrayList<>();
        int proyectosRentables = 0;

        for (int i = 0; i < simulaciones; i++) {
            // 1. Simular Inversión (Triangular alrededor de la base)
            double r1 = generarNumero(semilla, a, c, m);
            semilla = (a * semilla + c) % m;
            double inversion = simularTriangular(r1, inversionBase * 0.6, inversionBase, inversionBase * 1.4);

            // 2. Simular Flujo Anual (Triangular alrededor de la base)
            double r2 = generarNumero(semilla, a, c, m);
            semilla = (a * semilla + c) % m;
            double flujoAnual = simularTriangular(r2, flujoBase * 0.4, flujoBase, flujoBase * 1.6);

            // 3. Calcular TIR y VPN
            double tir = calcularTIR(inversion, flujoAnual, 5);
            tirs.add(tir);

            double vpn = calcularVPN(inversion, flujoAnual, 5, trema / 100);
            vpns.add(vpn);

            if (tir > trema) {
                proyectosRentables++;
            }
        }

        // 4. Estadísticas
        DoubleSummaryStatistics statsTIR = tirs.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        DoubleSummaryStatistics statsVPN = vpns.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        
        double probRentable = (double) proyectosRentables / simulaciones * 100;
        Collections.sort(tirs);
        double mediana = tirs.get(tirs.size() / 2);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("simulaciones", simulaciones);
        resultado.put("trema", trema);
        resultado.put("tir_promedio", Math.round(statsTIR.getAverage() * 100.0) / 100.0);
        resultado.put("tir_mediana", Math.round(mediana * 100.0) / 100.0);
        resultado.put("tir_min", Math.round(statsTIR.getMin() * 100.0) / 100.0);
        resultado.put("tir_max", Math.round(statsTIR.getMax() * 100.0) / 100.0);
        resultado.put("vpn_promedio", Math.round(statsVPN.getAverage() * 100.0) / 100.0);
        resultado.put("prob_rentable", Math.round(probRentable * 100.0) / 100.0);
        resultado.put("recomendacion", probRentable >= 90 ? "SÍ vale la pena" : "NO vale la pena");
        
        resultado.put("inversion_base", inversionBase);
        resultado.put("flujo_base", flujoBase);
        resultado.put("todas_las_tirs", tirs);

        return resultado;
    }

    // Métodos auxiliares
    private double generarNumero(long semilla, long a, long c, long m) {
        return (double) ((a * semilla + c) % m) / m;
    }

    private double simularTriangular(double r, double min, double moda, double max) {
        double f = (moda - min) / (max - min);
        if (r <= f) {
            return min + Math.sqrt(r * (max - min) * (moda - min));
        } else {
            return max - Math.sqrt((1 - r) * (max - min) * (max - moda));
        }
    }

    private double calcularTIR(double inversion, double flujo, int anios) {
        // Búsqueda binaria para encontrar la tasa que hace VPN = 0
        double low = -0.99, high = 10.0, mid = 0;
        for (int i = 0; i < 100; i++) {
            mid = (low + high) / 2;
            double vpn = -inversion;
            for (int t = 1; t <= anios; t++) {
                vpn += flujo / Math.pow(1 + mid, t);
            }
            if (vpn > 0) low = mid; else high = mid;
        }
        return mid * 100; // Retornamos en porcentaje
    }

    private double calcularVPN(double inversion, double flujo, int anios, double tasa) {
        double vpn = -inversion;
        for (int t = 1; t <= anios; t++) {
            vpn += flujo / Math.pow(1 + tasa, t);
        }
        return vpn;
    }
        // 19. Exportar Reporte a Excel (Datos + Estadísticas + Gráficos)
    @GetMapping("/exportar-excel-titulados")
    public void exportarExcelTitulados(HttpServletResponse response) throws IOException {
        // 1. Configurar la respuesta HTTP para descargar el archivo
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=Reporte_Titulados.xlsx");

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // --- HOJA 1: DATOS ---
            Sheet sheetDatos = workbook.createSheet("Datos de Titulados");
            Row header = sheetDatos.createRow(0);
            String[] columnas = {"ID", "Edad", "Género", "Área de Interés", "Situación Laboral"};
            for (int i = 0; i < columnas.length; i++) {
                header.createCell(i).setCellValue(columnas[i]);
            }

            List<Titulado> titulados = tituladoRepository.findAll();
            int rowNum = 1;
            for (Titulado t : titulados) {
                Row row = sheetDatos.createRow(rowNum++);
                row.createCell(0).setCellValue(t.getId());
                row.createCell(1).setCellValue(getValor(t, "EDAD"));
                row.createCell(2).setCellValue(getValor(t, "GÉNERO"));
                row.createCell(3).setCellValue(getValor(t, "EN CUÁL DE LAS SIGUIENTES ÁREAS"));
                row.createCell(4).setCellValue(getValor(t, "SITUACIÓN LABORAL ACTUAL"));
            }

            // --- HOJA 2: ESTADÍSTICAS ---
            Sheet sheetStats = workbook.createSheet("Estadísticas y Simulación");
            // ... (Aquí puedes agregar las estadísticas descriptivas y los resultados de las simulaciones)
            Row statsHeader = sheetStats.createRow(0);
            statsHeader.createCell(0).setCellValue("Métrica");
            statsHeader.createCell(1).setCellValue("Valor");

            // Ejemplo: Agregar los resultados de la simulación de TIR
            Map<String, Object> tirResult = simularTIR(1000, 15.0, 8000, 5000);            Row rowTIR = sheetStats.createRow(1);
            rowTIR.createCell(0).setCellValue("TIR Promedio Simulada");
            rowTIR.createCell(1).setCellValue((Double) tirResult.get("tir_promedio"));

            // --- HOJA 3: GRÁFICOS (Imágenes) ---
            Sheet sheetGraficos = workbook.createSheet("Gráficos");
            // ... (Aquí insertarías las imágenes de los gráficos generados con Java2D)
            // Para simplificar, te muestro cómo insertar una imagen desde un archivo local
            /*
            InputStream inputStream = new FileInputStream("ruta/a/tu/grafico.png");
            byte[] bytes = IOUtils.toByteArray(inputStream);
            int pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
            XSSFDrawing drawing = (XSSFDrawing) sheetGraficos.createDrawingPatriarch();
            XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, 0, 0, 10, 10);
            drawing.createPicture(anchor, pictureIdx);
            */

            // Escribir el archivo en la respuesta
            workbook.write(response.getOutputStream());
        }
    }

        // 20. Exportar Reporte de Empleadores a Excel
    @GetMapping("/exportar-excel-empleadores")
    public void exportarExcelEmpleadores(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=Reporte_Empleadores.xlsx");

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // --- HOJA 1: DATOS DE EMPLEADORES ---
            Sheet sheetDatos = workbook.createSheet("Datos de Empleadores");
            Row header = sheetDatos.createRow(0);
            String[] columnas = {"ID", "Nombre de la Empresa", "Tipo", "Tamaño", "Rubro", "Nivel de Formación Demandado"};
            for (int i = 0; i < columnas.length; i++) header.createCell(i).setCellValue(columnas[i]);

            List<Empleador> empleadores = empleadorRepository.findAll();
            int rowNum = 1;
            for (Empleador e : empleadores) {
                Row row = sheetDatos.createRow(rowNum++);
                row.createCell(0).setCellValue(e.getId());
                row.createCell(1).setCellValue(e.getNombreEmpresa() != null ? e.getNombreEmpresa() : "N/A");
                row.createCell(2).setCellValue(getValorEmpleador(e, "TIPO DE ORGANIZACIÓN"));
                // Limpiamos el tamaño para que se vea bonito
                String tamano = getValorEmpleador(e, "TAMAÑO DE LA ORGANIZACIÓN").split("\\(")[0].trim();
                row.createCell(3).setCellValue(tamano);
                row.createCell(4).setCellValue(getValorEmpleador(e, "RUBRO O SECTOR PRINCIPAL"));
                row.createCell(5).setCellValue(getValorEmpleador(e, "NIVEL DE FORMACIÓN ACADÉMICA"));
            }

            // --- HOJA 2: ESTADÍSTICAS DE DEMANDA ---
            Sheet sheetStats = workbook.createSheet("Estadísticas de Demanda");
            Row statsHeader = sheetStats.createRow(0);
            statsHeader.createCell(0).setCellValue("Métrica");
            statsHeader.createCell(1).setCellValue("Valor");

            // Calcular algunos datos clave
            Map<String, Long> porNivel = new HashMap<>();
            Map<String, Long> porTipo = new HashMap<>();
            for (Empleador e : empleadores) {
                porNivel.merge(getValorEmpleador(e, "NIVEL DE FORMACIÓN ACADÉMICA"), 1L, Long::sum);
                porTipo.merge(getValorEmpleador(e, "TIPO DE ORGANIZACIÓN"), 1L, Long::sum);
            }

            int rowStats = 1;
            Row rowTotal = sheetStats.createRow(rowStats++);
            rowTotal.createCell(0).setCellValue("Total de Empleadores Encuestados");
            rowTotal.createCell(1).setCellValue(empleadores.size());

            for (Map.Entry<String, Long> entry : porNivel.entrySet()) {
                Row row = sheetStats.createRow(rowStats++);
                row.createCell(0).setCellValue("Demandan Nivel: " + entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
            }

            for (Map.Entry<String, Long> entry : porTipo.entrySet()) {
                Row row = sheetStats.createRow(rowStats++);
                row.createCell(0).setCellValue("Tipo de Organización: " + entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
            }

            workbook.write(response.getOutputStream());
        }
    }

        // 21. Guardar Nueva Encuesta de Titulado (desde Formulario Web)
    @PostMapping("/guardar-titulado")
    public ResponseEntity<Map<String, String>> guardarTitulado(@RequestBody Map<String, Object> respuestas) {
        try {
            Titulado nuevo = new Titulado();
            nuevo.setFechaEncuesta(java.time.LocalDateTime.now());
            nuevo.setRespuestas(respuestas);
            tituladoRepository.save(nuevo);
            return ResponseEntity.ok(Collections.singletonMap("mensaje", "Titulado guardado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    // 22. Guardar Nueva Encuesta de Empleador (desde Formulario Web)
    @PostMapping("/guardar-empleador")
    public ResponseEntity<Map<String, String>> guardarEmpleador(@RequestBody Map<String, Object> respuestas) {
        try {
            Empleador nuevo = new Empleador();
            nuevo.setFechaEncuesta(java.time.LocalDateTime.now());
            // Extraemos el nombre de la empresa si viene en el mapa
            String nombre = (String) respuestas.entrySet().stream()
                .filter(e -> e.getKey().toUpperCase().contains("NOMBRE DE LA EMPRESA"))
                .map(Map.Entry::getValue).findFirst().orElse("N/A");
            nuevo.setNombreEmpresa(nombre);
            nuevo.setRespuestas(respuestas);
            empleadorRepository.save(nuevo);
            return ResponseEntity.ok(Collections.singletonMap("mensaje", "Empleador guardado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
        
    }

        // 23. Resumen Ejecutivo para el Dashboard de Titulados
       @GetMapping("/dashboard-titulados")
    public Map<String, Object> getDashboardTitulados() {
        List<Titulado> titulados = tituladoRepository.findAll();
        int total = titulados.size();
        
        List<Double> edades = new ArrayList<>();
        List<Double> aniosExperiencia = new ArrayList<>();
        List<Double> tiemposDesempleo = new ArrayList<>();
        List<Double> satisfaccion = new ArrayList<>();
        
        int interesadosPosgrado = 0;
        int conFormacionComplementaria = 0;
        int trabajando = 0;

        Map<String, Long> genero = new HashMap<>();
        Map<String, Long> area = new HashMap<>();
        Map<String, Long> financiamiento = new HashMap<>();
        Map<String, Long> situacion = new HashMap<>();

        for (Titulado t : titulados) {
            // 1. Edad
            String edadStr = getValor(t, "EDAD");
            if (edadStr.contains("19 - 22")) edades.add(20.5);
            else if (edadStr.contains("23 - 26")) edades.add(24.5);
            else if (edadStr.contains("27 - 30")) edades.add(28.5);
            else if (edadStr.contains("31 - 34")) edades.add(32.5);
            else if (edadStr.contains("35")) edades.add(37.0);

            // 2. Años de Experiencia
            String aniosStr = getValor(t, "AÑOS DE VIDA PROFESIONAL").replaceAll("[^0-9.]", "");
            if (!aniosStr.isEmpty()) aniosExperiencia.add(Double.parseDouble(aniosStr));

            // 3. Tiempo Desempleo
            String desempleoStr = getValor(t, "CANTIDAD CORRESPONDE AL TOTAL DE TIEMPO").replaceAll("[^0-9.]", "");
            if (!desempleoStr.isEmpty()) tiemposDesempleo.add(Double.parseDouble(desempleoStr));

            // 4. Satisfacción
            satisfaccion.add(convertirLikert(getValor(t, "GRADO DE SATISFACCIÓN")));

            // 5. Interés en Posgrado
            if (getValor(t, "ESTARÍA INTERESADO").toUpperCase().contains("SI")) interesadosPosgrado++;

            // 6. Formación Complementaria
            if (getValor(t, "HA REALIZADO O SE ENCUENTRA").toUpperCase().contains("SI")) conFormacionComplementaria++;

            // 7. Situación Laboral
            String sit = getValor(t, "SITUACIÓN LABORAL ACTUAL");
            if (sit.contains("trabajando") || sit.contains("emprendimiento")) trabajando++;

            // 8. Gráficos
            genero.merge(getValor(t, "GÉNERO"), 1L, Long::sum);
            area.merge(getValor(t, "EN CUÁL DE LAS SIGUIENTES ÁREAS"), 1L, Long::sum);
            
            String fin = getValor(t, "FUENTE DE FINANCIAMIENTO");
            if (fin.contains("PROPIOS")) fin = "Recursos propios";
            else if (fin.contains("BECA") && fin.contains("PARCIAL")) fin = "Beca parcial";
            else if (fin.contains("BECA")) fin = "Beca total";
            else if (fin.equals("N/A")) fin = "No aplica";
            financiamiento.merge(fin, 1L, Long::sum);

            if (sit.contains("trabajando")) sit = "Empleado";
            else if (sit.contains("NO trabajo")) sit = "Desempleado";
            else if (sit.contains("emprendimiento")) sit = "Emprendedor";
            situacion.merge(sit, 1L, Long::sum);
        }

        // Calcular todas las métricas
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("total_titulados", total);
        
        if (!edades.isEmpty()) {
            DoubleSummaryStatistics statsEdad = edades.stream().mapToDouble(Double::doubleValue).summaryStatistics();
            resultado.put("edad_media", Math.round(statsEdad.getAverage() * 100.0) / 100.0);
            Collections.sort(edades);
            resultado.put("edad_mediana", edades.get(edades.size() / 2));
            double varianza = 0;
            for (double e : edades) varianza += Math.pow(e - statsEdad.getAverage(), 2);
            resultado.put("edad_desviacion", Math.round(Math.sqrt(varianza / edades.size()) * 100.0) / 100.0);
        }

        resultado.put("promedio_anios_experiencia", Math.round(aniosExperiencia.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100.0) / 100.0);
        resultado.put("promedio_desempleo", Math.round(tiemposDesempleo.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100.0) / 100.0);
        resultado.put("promedio_satisfaccion", Math.round(satisfaccion.stream().mapToDouble(Double::doubleValue).average().orElse(0) / 4 * 5 * 10.0) / 10.0);
        
        resultado.put("tasa_interes_posgrado", total > 0 ? Math.round(((double) interesadosPosgrado / total * 100) * 100.0) / 100.0 : 0);
        resultado.put("tasa_formacion_complementaria", total > 0 ? Math.round(((double) conFormacionComplementaria / total * 100) * 100.0) / 100.0 : 0);
        resultado.put("tasa_empleo", total > 0 ? Math.round(((double) trabajando / total * 100) * 100.0) / 100.0 : 0);

        resultado.put("por_genero", genero);
        resultado.put("por_area", area);
        resultado.put("por_financiamiento", financiamiento);
        resultado.put("por_situacion", situacion);

        return resultado;
    }
    private double convertirLikert(String val) {
        if (val == null) return 0;
        if (val.contains("Totalmente en desacuerdo")) return 1.0;
        if (val.contains("En desacuerdo") && !val.contains("Totalmente")) return 2.0;
        if (val.contains("Totalmente de Acuerdo")) return 4.0;
        if (val.contains("De acuerdo")) return 3.0;
        return 0;
    }

        // 24. Resumen Ejecutivo para el Dashboard de Empleadores
    @GetMapping("/dashboard-empleadores")
    public Map<String, Object> getDashboardEmpleadores() {
        List<Empleador> empleadores = empleadorRepository.findAll();
        int total = empleadores.size();
        
        // Contadores para las tarjetas KPI
        int contrataronUltimos5 = 0;
        int altaProbabilidad = 0;
        int demandanMaestria = 0;
        int demandanLicenciatura = 0;
        
        Map<String, Long> porTipo = new HashMap<>();
        Map<String, Long> porTamano = new HashMap<>();
        Map<String, Long> porRubro = new HashMap<>();
        Map<String, Long> porNivel = new HashMap<>();

        for (Empleador e : empleadores) {
            // 1. Contratación
            String contrato = getValorEmpleador(e, "HA CONTRATADO INGENIEROS");
            if (contrato.toUpperCase().contains("SI")) contrataronUltimos5++;
            
            // 2. Posibilidad de contratación futura
            String posibilidad = getValorEmpleador(e, "POSIBILIDAD DE INCORPORAR");
            if (posibilidad.contains("alta probabilidad")) altaProbabilidad++;
            
            // 3. Nivel de formación demandado
            String nivel = getValorEmpleador(e, "NIVEL DE FORMACIÓN ACADÉMICA");
            if (nivel.toUpperCase().contains("MAESTRIA") || nivel.toUpperCase().contains("MAESTRÍA")) demandanMaestria++;
            if (nivel.toUpperCase().contains("LICENCIATURA")) demandanLicenciatura++;
            
            // 4. Conteos para gráficos
            porTipo.merge(getValorEmpleador(e, "TIPO DE ORGANIZACIÓN"), 1L, Long::sum);
            String tamano = getValorEmpleador(e, "TAMAÑO DE LA ORGANIZACIÓN").split("\\(")[0].trim();
            porTamano.merge(tamano, 1L, Long::sum);
            
            String rubro = getValorEmpleador(e, "RUBRO O SECTOR PRINCIPAL");
            if (rubro.length() > 30) rubro = rubro.substring(0, 30) + "...";
            porRubro.merge(rubro, 1L, Long::sum);
            
            porNivel.merge(nivel, 1L, Long::sum);
        }

        // Calcular tasas y promedios
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("total_empleadores", total);
        resultado.put("tasa_contratacion", total > 0 ? Math.round(((double) contrataronUltimos5 / total * 100) * 100.0) / 100.0 : 0);
        resultado.put("tasa_alta_probabilidad", total > 0 ? Math.round(((double) altaProbabilidad / total * 100) * 100.0) / 100.0 : 0);
        resultado.put("demanda_maestria", total > 0 ? Math.round(((double) demandanMaestria / total * 100) * 100.0) / 100.0 : 0);
        resultado.put("demanda_licenciatura", total > 0 ? Math.round(((double) demandanLicenciatura / total * 100) * 100.0) / 100.0 : 0);

        resultado.put("por_tipo", porTipo);
        resultado.put("por_tamano", porTamano);
        resultado.put("por_rubro", porRubro);
        resultado.put("por_nivel", porNivel);

        return resultado;
    }

        // 27. Chi-Cuadrado Dinámico para Empleadores
    @GetMapping("/chi-cuadrado-empleadores-dinamico")
    public Map<String, Object> calcularChiCuadradoEmpleadoresDinamico(
            @RequestParam(defaultValue = "TIPO DE ORGANIZACIÓN") String variable1,
            @RequestParam(defaultValue = "NIVEL DE FORMACIÓN ACADÉMICA") String variable2) {

        List<Empleador> empleadores = empleadorRepository.findAll();
        
        // 1. Identificar valores únicos
        Set<String> filas = new TreeSet<>();
        Set<String> columnas = new TreeSet<>();
        for (Empleador e : empleadores) {
            String v1 = getValorEmpleador(e, variable1);
            String v2 = getValorEmpleador(e, variable2);
            if (!v1.equals("N/A") && !v2.equals("N/A")) {
                filas.add(v1);
                columnas.add(v2);
            }
        }

        // 2. Matriz FO
        Map<String, Map<String, Integer>> matrizFO = new LinkedHashMap<>();
        Map<String, Integer> totalesFila = new LinkedHashMap<>();
        Map<String, Integer> totalesColumna = new LinkedHashMap<>();
        int totalGeneral = 0;

        for (String f : filas) {
            matrizFO.put(f, new LinkedHashMap<>());
            totalesFila.put(f, 0);
        }
        for (String c : columnas) {
            totalesColumna.put(c, 0);
        }

        for (Empleador e : empleadores) {
            String v1 = getValorEmpleador(e, variable1);
            String v2 = getValorEmpleador(e, variable2);
            if (filas.contains(v1) && columnas.contains(v2)) {
                matrizFO.get(v1).merge(v2, 1, Integer::sum);
                totalesFila.merge(v1, 1, Integer::sum);
                totalesColumna.merge(v2, 1, Integer::sum);
                totalGeneral++;
            }
        }

        // 3. Matriz FE y Chi-Cuadrado
        Map<String, Map<String, Double>> matrizFE = new LinkedHashMap<>();
        double chi = 0.0;

        for (String f : filas) {
            matrizFE.put(f, new LinkedHashMap<>());
            for (String c : columnas) {
                int fo = matrizFO.get(f).getOrDefault(c, 0);
                int tf = totalesFila.get(f);
                int tc = totalesColumna.get(c);
                double fe = (double) (tf * tc) / totalGeneral;
                
                matrizFE.get(f).put(c, Math.round(fe * 100.0) / 100.0);
                
                if (fe > 0) {
                    chi += Math.pow(fo - fe, 2) / fe;
                }
            }
        }

        int gl = (filas.size() - 1) * (columnas.size() - 1);
        double critico = 0;
        if (gl == 1) critico = 3.841;
        else if (gl == 2) critico = 5.991;
        else if (gl == 3) critico = 7.815;
        else if (gl == 4) critico = 9.488;
        else if (gl == 5) critico = 11.070;
        else if (gl == 6) critico = 12.592;
        else if (gl == 8) critico = 15.507;
        else if (gl == 10) critico = 18.307;
        else critico = 20.0;

        boolean independiente = chi < critico;

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("filas", filas);
        resultado.put("columnas", columnas);
        resultado.put("matriz_fo", matrizFO);
        resultado.put("matriz_fe", matrizFE);
        resultado.put("totales_fila", totalesFila);
        resultado.put("totales_columna", totalesColumna);
        resultado.put("total_general", totalGeneral);
        resultado.put("chi_calculado", Math.round(chi * 100.0) / 100.0);
        resultado.put("grados_libertad", gl);
        resultado.put("valor_critico", critico);
        resultado.put("es_independiente", independiente);
        resultado.put("conclusion", independiente ? 
            "Las variables son INDEPENDIENTES (no hay relación significativa)" : 
            "Las variables NO son independientes (existe relación significativa)");

        return resultado;
    }
}