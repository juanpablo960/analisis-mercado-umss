package com.umss.sistemas.tallerss.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class SimulacionService {

    // 1. Generador Congruencial Mixto (Coss Bu - Cap 2.1.1)
    // X_{n+1} = (a * X_n + c) mod m
    private long semilla = 12345;
    private final long a = 1103515245;
    private final long c = 12345;
    private final long m = (long) Math.pow(2, 31);

    public double generarAleatorio() {
        semilla = (a * semilla + c) % m;
        return (double) semilla / m;
    }

    // 2. Simulación de Empleabilidad (Basado en los datos de la encuesta)
    // Usamos la Transformada Inversa para simular el tiempo para conseguir empleo
    public Map<String, Object> simularEmpleabilidad(int corridas) {
        List<Double> tiempos = new ArrayList<>();
        int exitosos = 0;

        // Basado en los datos reales de la encuesta (aprox):
        // 50% consigue trabajo en menos de 4 meses, 50% tarda más.
        // Usaremos una distribución empírica simple para el ejemplo.
        for (int i = 0; i < corridas; i++) {
            double r = generarAleatorio();
            double tiempo;
            
            // Lógica de Transformada Inversa (simplificada para el ejemplo)
            if (r < 0.30) { // 30% consigue en < 1 mes
                tiempo = 0.5 + (r * 3); 
            } else if (r < 0.60) { // 30% consigue entre 1-4 meses
                tiempo = 1 + ((r - 0.30) * 10); 
            } else if (r < 0.80) { // 20% consigue entre 4-8 meses
                tiempo = 4 + ((r - 0.60) * 20);
            } else { // 20% tarda más de 8 meses
                tiempo = 8 + ((r - 0.80) * 50);
            }
            
            tiempos.add(tiempo);
            if (tiempo <= 6) { // Consideramos "éxito" si consigue trabajo en menos de 6 meses
                exitosos++;
            }
        }

        // Calcular estadísticas
        DoubleSummaryStatistics stats = tiempos.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        
        // Calcular Intervalo de Confianza (95%) - Coss Bu Cap 6.1
        double media = stats.getAverage();
        double desviacion = calcularDesviacionEstandar(tiempos, media);
        double errorEstandar = desviacion / Math.sqrt(corridas);
        double margenError = 1.96 * errorEstandar; // 1.96 para 95% de confianza

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("corridas", corridas);
        resultado.put("tiempo_promedio_meses", Math.round(media * 100.0) / 100.0);
        resultado.put("probabilidad_exito", Math.round((double) exitosos / corridas * 10000.0) / 100.0); // %
        resultado.put("intervalo_confianza_inferior", Math.round((media - margenError) * 100.0) / 100.0);
        resultado.put("intervalo_confianza_superior", Math.round((media + margenError) * 100.0) / 100.0);
        
        return resultado;
    }

    // 3. Simulación de Demanda de Posgrado (Proyecto de Inversión)
    public Map<String, Object> simularDemandaPosgrado(int corridas) {
        int interesadosIA = 0;
        int interesadosSoftware = 0;

        // Basado en el gráfico de pastel: IA (~45%), Ing Software (~25%)
        for (int i = 0; i < corridas; i++) {
            double r = generarAleatorio();
            if (r < 0.45) {
                interesadosIA++;
            } else if (r < 0.70) {
                interesadosSoftware++;
            }
        }

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("corridas", corridas);
        resultado.put("demanda_ia", Math.round((double) interesadosIA / corridas * 10000.0) / 100.0);
        resultado.put("demanda_software", Math.round((double) interesadosSoftware / corridas * 10000.0) / 100.0);
        
        return resultado;
    }

    private double calcularDesviacionEstandar(List<Double> valores, double media) {
        double suma = 0;
        for (Double v : valores) {
            suma += Math.pow(v - media, 2);
        }
        return Math.sqrt(suma / (valores.size() - 1));
    }
}