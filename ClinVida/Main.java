// ═══════════════════════════════════════════════════════════════════
//  SISTEMA DE ALERTAS MÉDICAS HOSPITALARIAS
//  Universidad Cooperativa de Colombia — Patrones de Software
//  Patrones implementados: ADAPTER + BRIDGE
//
//  CASO REAL:
//  El hospital "ClinVida" tiene 3 sistemas legacy de registros
//  médicos con interfaces completamente distintas.
//  Además, necesita enviar alertas (emergencias, citas, resultados)
//  por múltiples canales (Email, SMS, WhatsApp) de forma desacoplada.
//
//  ADAPTER → Unifica los 3 sistemas legacy bajo RegistroMedicoTarget
//  BRIDGE  → Separa el TIPO de alerta del CANAL de notificación
// ═══════════════════════════════════════════════════════════════════

import java.util.*;
import java.time.*;
import java.time.format.*;

// ───────────────────────────────────────────────────────────────────
//  SECCIÓN 1 — PATRÓN ADAPTER
//  Problema: 3 sistemas legacy con métodos totalmente distintos.
//  Solución: Un Adapter por sistema que los adapta a la interfaz
//            moderna PasarelaMedica (Target).
// ───────────────────────────────────────────────────────────────────

// TARGET: Interfaz moderna que el hospital espera de cualquier sistema
interface PasarelaMedica {
    Paciente buscarPaciente(String id);

    boolean registrarConsulta(String pacienteId, String diagnostico, String medico);

    List<String> obtenerHistorial(String pacienteId);

    double calcularCosto(String pacienteId, String tipoServicio);
}

// Modelo de datos estándar del hospital moderno
class Paciente {
    public final String id;
    public final String nombre;
    public final int edad;
    public final String tipoSangre;
    public final List<String> alergias;

    public Paciente(String id, String nombre, int edad, String tipoSangre, List<String> alergias) {
        this.id = id;
        this.nombre = nombre;
        this.edad = edad;
        this.tipoSangre = tipoSangre;
        this.alergias = alergias;
    }

    @Override
    public String toString() {
        return String.format("Paciente{id='%s', nombre='%s', edad=%d, sangre='%s', alergias=%s}",
                id, nombre, edad, tipoSangre, alergias);
    }
}

// ── ADAPTEE 1: ClinicSoft — sistema de clínica privada legacy ──────
// Usa XML-strings, IDs numéricos, precios en centavos, sin historial real
class ClinicSoftLegacy {
    private static final Map<Integer, String[]> PACIENTES_XML = new HashMap<>();
    private static final Map<Integer, List<String>> CONSULTAS = new HashMap<>();

    static {
        // Formato: {nombre, edad, sangre, alergia1|alergia2}
        PACIENTES_XML.put(1001, new String[] { "Carlos Mendoza", "34", "O+", "Penicilina" });
        PACIENTES_XML.put(1002, new String[] { "Ana Ríos", "28", "A-", "Ninguna" });
        PACIENTES_XML.put(1003, new String[] { "Luis Herrera", "56", "B+", "Aspirina|Ibuprofeno" });
        CONSULTAS.put(1001, new ArrayList<>(Arrays.asList("2024-01-10: Gripe - Dr. Vargas")));
        CONSULTAS.put(1002, new ArrayList<>());
        CONSULTAS.put(1003, new ArrayList<>(Arrays.asList(
                "2023-11-05: Hipertensión - Dr. Salcedo",
                "2024-02-20: Control cardio - Dr. Salcedo")));
    }

    // Método legacy: recibe ID numérico, retorna XML como String
    public String fetchPatientXML(int numericId) {
        String[] data = PACIENTES_XML.get(numericId);
        if (data == null)
            return "<error>NOT_FOUND</error>";
        return String.format(
                "<patient><name>%s</name><age>%s</age><blood>%s</blood><allergies>%s</allergies></patient>",
                data[0], data[1], data[2], data[3]);
    }

    // Guarda consulta concatenando a un string plano
    public boolean saveVisitRecord(int patientId, String visitText) {
        if (!PACIENTES_XML.containsKey(patientId))
            return false;
        CONSULTAS.computeIfAbsent(patientId, k -> new ArrayList<>())
                .add(LocalDate.now() + ": " + visitText);
        System.out.println("[ClinicSoft] Visita guardada para ID " + patientId + ": " + visitText);
        return true;
    }

    // Retorna lista de strings planos
    public List<String> getVisitHistory(int patientId) {
        return CONSULTAS.getOrDefault(patientId, Collections.emptyList());
    }

    // Precios en centavos de COP
    public int getPriceCents(String serviceCode) {
        switch (serviceCode.toUpperCase()) {
            case "CONSULTA":
                return 8000000; // 80,000 COP
            case "URGENCIAS":
                return 25000000; // 250,000 COP
            case "LABORATORIO":
                return 12000000; // 120,000 COP
            default:
                return 5000000;
        }
    }
}

// ── ADAPTEE 2: MedProDB — sistema de EPS legacy ────────────────────
// Usa nombres de métodos en inglés antiguo, IDs por cédula, montos en USD
class MedProDB {
    private static final Map<String, Object[]> RECORDS = new HashMap<>();
    private static final Map<String, List<String>> MEDICAL_LOG = new HashMap<>();

    static {
        // Formato: {fullName, age, bloodGroup, allergiesArray}
        RECORDS.put("CC-1090432178", new Object[] { "Valentina Torres", 42, "AB+", new String[] { "Morfina" } });
        RECORDS.put("CC-5521019834", new Object[] { "Roberto Castillo", 19, "O-", new String[] {} });
        RECORDS.put("CC-8801234567", new Object[] { "María Suárez", 67, "A+", new String[] { "Latex", "Sulfas" } });
        MEDICAL_LOG.put("CC-1090432178", new ArrayList<>(Arrays.asList(
                "2024-03-01|CONSULTA|Dr. Peña|Diabetes control")));
        MEDICAL_LOG.put("CC-5521019834", new ArrayList<>());
        MEDICAL_LOG.put("CC-8801234567", new ArrayList<>(Arrays.asList(
                "2023-09-14|URGENCIAS|Dr. Mora|Caída",
                "2024-01-22|LAB|Dr. Mora|Hemograma completo")));
    }

    // Retorna array de Objects con datos mezclados
    public Object[] lookupRecord(String cedula) {
        return RECORDS.getOrDefault(cedula, null);
    }

    // Guarda en formato pipe-separated
    public int insertMedicalLog(String cedula, String type, String doctor, String notes) {
        if (!RECORDS.containsKey(cedula))
            return -1;
        String entry = LocalDate.now() + "|" + type + "|" + doctor + "|" + notes;
        MEDICAL_LOG.computeIfAbsent(cedula, k -> new ArrayList<>()).add(entry);
        System.out.println("[MedProDB] Log insertado -> " + cedula + " | " + entry);
        return 1; // rows affected
    }

    // Retorna logs con pipe-separator
    public List<String> fetchLogs(String cedula) {
        return MEDICAL_LOG.getOrDefault(cedula, Collections.emptyList());
    }

    // Precios en USD con decimales
    public double getPriceUSD(String procedureType) {
        switch (procedureType.toUpperCase()) {
            case "CONSULTA":
                return 22.50;
            case "URGENCIAS":
                return 85.00;
            case "LABORATORIO":
                return 35.00;
            default:
                return 15.00;
        }
    }
}

// ── ADAPTEE 3: HospitalAPI — sistema estatal legacy ────────────────
// Usa REST-style Maps, IDs por UUID, precios fijos por tabla
class HospitalAPILegacy {
    private static final Map<String, Map<String, Object>> PATIENT_DB = new HashMap<>();
    private static final Map<String, List<Map<String, String>>> VISIT_DB = new HashMap<>();

    static {
        Map<String, Object> p1 = new HashMap<>();
        p1.put("patient_name", "Jorge Ibáñez");
        p1.put("patient_age", 38);
        p1.put("blood_type", "B-");
        p1.put("allergy_list", Arrays.asList("Quinina"));
        PATIENT_DB.put("UUID-A1B2C3", p1);

        Map<String, Object> p2 = new HashMap<>();
        p2.put("patient_name", "Sandra Muñoz");
        p2.put("patient_age", 51);
        p2.put("blood_type", "AB-");
        p2.put("allergy_list", Arrays.asList("Yodo", "Contraste"));
        PATIENT_DB.put("UUID-D4E5F6", p2);

        VISIT_DB.put("UUID-A1B2C3", new ArrayList<>());
        VISIT_DB.put("UUID-D4E5F6", new ArrayList<>(Arrays.asList(
                new HashMap<String, String>() {
                    {
                        put("date", "2024-02-28");
                        put("type", "CONSULTA");
                        put("doctor", "Dr. Lozano");
                        put("notes", "Artritis seguimiento");
                    }
                })));
    }

    // Retorna Map con claves snake_case
    public Map<String, Object> getPatient(String uuid) {
        return PATIENT_DB.getOrDefault(uuid, Collections.emptyMap());
    }

    // Recibe Map de visita completo
    public boolean postVisit(String uuid, Map<String, String> visitData) {
        if (!PATIENT_DB.containsKey(uuid))
            return false;
        VISIT_DB.computeIfAbsent(uuid, k -> new ArrayList<>()).add(visitData);
        System.out.println("[HospitalAPI] POST /visits -> " + uuid + " | " + visitData);
        return true;
    }

    // Retorna List<Map> de visitas
    public List<Map<String, String>> getVisits(String uuid) {
        return VISIT_DB.getOrDefault(uuid, Collections.emptyList());
    }

    // Tarifas SGSSS por código CUPS
    public double getCUPSPrice(String cupsCode) {
        Map<String, Double> tariff = new HashMap<>();
        tariff.put("CONSULTA", 55000.0);
        tariff.put("URGENCIAS", 180000.0);
        tariff.put("LABORATORIO", 75000.0);
        return tariff.getOrDefault(cupsCode, 30000.0);
    }
}

// ── ADAPTER 1: Adapta ClinicSoft al Target PasarelaMedica ─────────
class ClinicSoftAdapter implements PasarelaMedica {
    private final ClinicSoftLegacy clinicSoft;
    private static final double CENTAVOS_A_PESOS = 0.01;
    private static final double PESOS_A_USD = 0.00025; // tasa aprox

    public ClinicSoftAdapter(ClinicSoftLegacy clinicSoft) {
        this.clinicSoft = clinicSoft;
    }

    @Override
    public Paciente buscarPaciente(String id) {
        // Adaptar: String "CS-1001" → int 1001, XML → Paciente
        try {
            int numericId = Integer.parseInt(id.replace("CS-", ""));
            String xml = clinicSoft.fetchPatientXML(numericId);
            if (xml.contains("<error>"))
                return null;

            // Parseo manual del XML legacy (tags cortos del sistema ClinicSoft)
            String nombre = extractXML(xml, "n"); // <n> = nombre
            int edad = Integer.parseInt(extractXML(xml, "age"));
            String sangre = extractXML(xml, "blood");
            String alergiaStr = extractXML(xml, "allergies");
            List<String> alergias = alergiaStr.equals("Ninguna")
                    ? Collections.emptyList()
                    : Arrays.asList(alergiaStr.split("\\|"));

            System.out.println("[ClinicSoftAdapter] XML parseado para " + id);
            return new Paciente(id, nombre, edad, sangre, alergias);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean registrarConsulta(String pacienteId, String diagnostico, String medico) {
        // Adaptar: String id → int, formato → texto plano
        try {
            int numericId = Integer.parseInt(pacienteId.replace("CS-", ""));
            String visitText = diagnostico + " — " + medico;
            return clinicSoft.saveVisitRecord(numericId, visitText);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public List<String> obtenerHistorial(String pacienteId) {
        // Adaptar: int id → lista estándar
        try {
            int numericId = Integer.parseInt(pacienteId.replace("CS-", ""));
            return clinicSoft.getVisitHistory(numericId);
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public double calcularCosto(String pacienteId, String tipoServicio) {
        // Adaptar: centavos COP → COP entero
        int centavos = clinicSoft.getPriceCents(tipoServicio.toUpperCase());
        double costoBase = centavos * CENTAVOS_A_PESOS;
        // Lógica adicional: recargo 15% si paciente tiene alergias
        Paciente p = buscarPaciente(pacienteId);
        double recargo = (p != null && !p.alergias.isEmpty()) ? 1.15 : 1.0;
        System.out.printf("[ClinicSoftAdapter] Costo %s: $%.2f COP (recargo: x%.2f)%n",
                tipoServicio, costoBase * recargo, recargo);
        return costoBase * recargo;
    }

    private String extractXML(String xml, String tag) {
        String openTag = "<" + tag + ">";
        String closeTag = "</" + tag + ">";
        int start = xml.indexOf(openTag);
        int end = xml.indexOf(closeTag);
        if (start < 0 || end < 0)
            return "";
        return xml.substring(start + openTag.length(), end);
    }
}

// ── ADAPTER 2: Adapta MedProDB al Target PasarelaMedica ───────────
class MedProAdapter implements PasarelaMedica {
    private final MedProDB medPro;
    private static final double USD_A_COP = 4100.0; // tasa referencia

    public MedProAdapter(MedProDB medPro) {
        this.medPro = medPro;
    }

    @Override
    public Paciente buscarPaciente(String id) {
        // Adaptar: "MP-CC-1090432178" → "CC-1090432178", Object[] → Paciente
        String cedula = id.replace("MP-", "");
        Object[] data = medPro.lookupRecord(cedula);
        if (data == null)
            return null;

        String nombre = (String) data[0];
        int edad = (int) data[1];
        String sangre = (String) data[2];
        List<String> alergias = Arrays.asList((String[]) data[3]);

        System.out.println("[MedProAdapter] Object[] adaptado para " + cedula);
        return new Paciente(id, nombre, edad, sangre, alergias);
    }

    @Override
    public boolean registrarConsulta(String pacienteId, String diagnostico, String medico) {
        // Adaptar: extraer tipo y notas del diagnóstico
        String cedula = pacienteId.replace("MP-", "");
        String tipo = diagnostico.toUpperCase().contains("URGENCIA") ? "URGENCIAS" : "CONSULTA";
        int rows = medPro.insertMedicalLog(cedula, tipo, medico, diagnostico);
        return rows > 0;
    }

    @Override
    public List<String> obtenerHistorial(String pacienteId) {
        // Adaptar: pipe-separated → lista legible
        String cedula = pacienteId.replace("MP-", "");
        List<String> raw = medPro.fetchLogs(cedula);
        List<String> formatted = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.split("\\|");
            if (parts.length >= 4) {
                formatted.add(parts[0] + ": " + parts[3] + " — " + parts[2] + " (" + parts[1] + ")");
            } else {
                formatted.add(entry);
            }
        }
        return formatted;
    }

    @Override
    public double calcularCosto(String pacienteId, String tipoServicio) {
        // Adaptar: USD → COP, aplicar descuento EPS si es adulto mayor
        double precioUSD = medPro.getPriceUSD(tipoServicio.toUpperCase());
        double precioCOP = precioUSD * USD_A_COP;
        // Lógica: descuento 20% para mayores de 60
        Paciente p = buscarPaciente(pacienteId);
        double descuento = (p != null && p.edad >= 60) ? 0.80 : 1.0;
        System.out.printf("[MedProAdapter] USD %.2f → COP %.2f (descuento: x%.2f)%n",
                precioUSD, precioCOP * descuento, descuento);
        return precioCOP * descuento;
    }
}

// ── ADAPTER 3: Adapta HospitalAPI al Target PasarelaMedica ────────
class HospitalAPIAdapter implements PasarelaMedica {
    private final HospitalAPILegacy api;

    public HospitalAPIAdapter(HospitalAPILegacy api) {
        this.api = api;
    }

    @Override
    public Paciente buscarPaciente(String id) {
        // Adaptar: "HA-UUID-A1B2C3" → "UUID-A1B2C3", Map → Paciente
        String uuid = id.replace("HA-", "");
        Map<String, Object> data = api.getPatient(uuid);
        if (data.isEmpty())
            return null;

        String nombre = (String) data.get("patient_name");
        int edad = (int) data.get("patient_age");
        String sangre = (String) data.get("blood_type");
        @SuppressWarnings("unchecked")
        List<String> alergias = (List<String>) data.get("allergy_list");

        System.out.println("[HospitalAPIAdapter] Map adaptado para " + uuid);
        return new Paciente(id, nombre, edad, sangre, alergias);
    }

    @Override
    public boolean registrarConsulta(String pacienteId, String diagnostico, String medico) {
        // Adaptar: parámetros planos → Map de visita
        String uuid = pacienteId.replace("HA-", "");
        Map<String, String> visitData = new HashMap<>();
        visitData.put("date", LocalDate.now().toString());
        visitData.put("type", "CONSULTA");
        visitData.put("doctor", medico);
        visitData.put("notes", diagnostico);
        return api.postVisit(uuid, visitData);
    }

    @Override
    public List<String> obtenerHistorial(String pacienteId) {
        // Adaptar: List<Map> → List<String> legible
        String uuid = pacienteId.replace("HA-", "");
        List<Map<String, String>> visits = api.getVisits(uuid);
        List<String> historial = new ArrayList<>();
        for (Map<String, String> v : visits) {
            historial.add(v.get("date") + ": " + v.get("notes") + " — " + v.get("doctor")
                    + " (" + v.get("type") + ")");
        }
        return historial;
    }

    @Override
    public double calcularCosto(String pacienteId, String tipoServicio) {
        // Adaptar: CUPS tariff → costo con IVA si aplica
        double base = api.getCUPSPrice(tipoServicio.toUpperCase());
        // Lógica: IVA 19% solo a servicios no-urgencias (regulación salud)
        boolean esUrgencia = tipoServicio.equalsIgnoreCase("URGENCIAS");
        double total = esUrgencia ? base : base * 1.19;
        System.out.printf("[HospitalAPIAdapter] CUPS $%.2f → Total $%.2f %s%n",
                base, total, esUrgencia ? "(sin IVA)" : "(+IVA 19%)");
        return total;
    }
}

// ───────────────────────────────────────────────────────────────────
// SECCIÓN 2 — PATRÓN BRIDGE
// Problema: Hay 3 tipos de alertas × 3 canales = 9 clases sin Bridge.
// Con Bridge: 3 abstracciones + 3 implementaciones = 6 clases.
// El Bridge "conecta" el TIPO de alerta con el CANAL de envío.
// ───────────────────────────────────────────────────────────────────

// IMPLEMENTACIÓN (interfaz de bajo nivel — el canal de envío)
interface CanalNotificacion {
    void enviarMensaje(String destinatario, String asunto, String cuerpo);

    String getNombreCanal();

    boolean validarDestinatario(String destinatario);
}

// IMPLEMENTACIÓN CONCRETA 1: Canal Email
class CanalEmail implements CanalNotificacion {
    private final List<String> mensajesEnviados = new ArrayList<>();

    @Override
    public void enviarMensaje(String destinatario, String asunto, String cuerpo) {
        if (!validarDestinatario(destinatario)) {
            System.out.println("[Email] ERROR: dirección inválida → " + destinatario);
            return;
        }
        String log = String.format("[Email] Para: %s | Asunto: %s | %s caracteres",
                destinatario, asunto, cuerpo.length());
        mensajesEnviados.add(log);
        System.out.println("══════════════════════════════════════");
        System.out.println("  📧 EMAIL ENVIADO");
        System.out.println("  Para:   " + destinatario);
        System.out.println("  Asunto: " + asunto);
        System.out.println("  Cuerpo: " + cuerpo);
        System.out.println("  Estado: ENTREGADO ✓");
        System.out.println("══════════════════════════════════════");
    }

    @Override
    public String getNombreCanal() {
        return "Email";
    }

    @Override
    public boolean validarDestinatario(String destinatario) {
        return destinatario != null && destinatario.contains("@") && destinatario.contains(".");
    }

    public List<String> getMensajesEnviados() {
        return mensajesEnviados;
    }
}

// IMPLEMENTACIÓN CONCRETA 2: Canal SMS
class CanalSMS implements CanalNotificacion {
    private static final int MAX_SMS_CHARS = 160;
    private int smsSentCount = 0;

    @Override
    public void enviarMensaje(String destinatario, String asunto, String cuerpo) {
        if (!validarDestinatario(destinatario)) {
            System.out.println("[SMS] ERROR: número inválido → " + destinatario);
            return;
        }
        // Lógica real: SMS tiene límite de 160 caracteres
        String mensajeSMS = ("[" + asunto + "] " + cuerpo);
        if (mensajeSMS.length() > MAX_SMS_CHARS) {
            int partes = (int) Math.ceil(mensajeSMS.length() / (double) MAX_SMS_CHARS);
            System.out.printf("[SMS] Mensaje largo (%d chars) → dividido en %d SMS%n",
                    mensajeSMS.length(), partes);
            // Enviar en partes
            for (int i = 0; i < partes; i++) {
                int start = i * MAX_SMS_CHARS;
                int end = Math.min(start + MAX_SMS_CHARS, mensajeSMS.length());
                String parte = mensajeSMS.substring(start, end);
                smsSentCount++;
                System.out.printf("[SMS %d/%d] → %s: %s%n", i + 1, partes, destinatario, parte);
            }
        } else {
            smsSentCount++;
            System.out.println("══════════════════════════════════════");
            System.out.println("  📱 SMS ENVIADO");
            System.out.println("  Para:    " + destinatario);
            System.out.println("  Mensaje: " + mensajeSMS);
            System.out.printf("  Chars:   %d/%d%n", mensajeSMS.length(), MAX_SMS_CHARS);
            System.out.println("  Estado:  ENTREGADO ✓");
            System.out.println("══════════════════════════════════════");
        }
    }

    @Override
    public String getNombreCanal() {
        return "SMS";
    }

    @Override
    public boolean validarDestinatario(String destinatario) {
        // Número colombiano: +57 seguido de 10 dígitos
        return destinatario != null && destinatario.matches("^\\+57[0-9]{10}$");
    }

    public int getSmsSentCount() {
        return smsSentCount;
    }
}

// IMPLEMENTACIÓN CONCRETA 3: Canal WhatsApp
class CanalWhatsApp implements CanalNotificacion {
    private final Map<String, Integer> mensajesPorContacto = new HashMap<>();

    @Override
    public void enviarMensaje(String destinatario, String asunto, String cuerpo) {
        if (!validarDestinatario(destinatario)) {
            System.out.println("[WhatsApp] ERROR: número no registrado → " + destinatario);
            return;
        }
        // Lógica: WhatsApp permite formato rico con emojis
        String msgFormateado = "🏥 *" + asunto + "*\n\n" + cuerpo + "\n\n_ClinVida Medical Center_";
        mensajesPorContacto.merge(destinatario, 1, Integer::sum);

        System.out.println("══════════════════════════════════════");
        System.out.println("  💬 WHATSAPP ENVIADO");
        System.out.println("  Para:    " + destinatario);
        System.out.println("  Mensaje: " + msgFormateado);
        System.out.printf("  Msgs enviados a este contacto: %d%n",
                mensajesPorContacto.get(destinatario));
        System.out.println("  Estado:  ENTREGADO ✓");
        System.out.println("══════════════════════════════════════");
    }

    @Override
    public String getNombreCanal() {
        return "WhatsApp";
    }

    @Override
    public boolean validarDestinatario(String destinatario) {
        // WhatsApp: número con código de país
        return destinatario != null && destinatario.matches("^\\+[0-9]{10,15}$");
    }
}

// ABSTRACCIÓN (interfaz de alto nivel — el tipo de alerta)
abstract class AlertaMedica {
    // El Bridge: la abstracción TIENE un canal (composición, no herencia)
    protected CanalNotificacion canal;
    protected String idAlerta;

    public AlertaMedica(CanalNotificacion canal) {
        this.canal = canal;
        this.idAlerta = "ALT-" + System.currentTimeMillis();
    }

    // Método abstracto que cada tipo de alerta implementa con su lógica
    public abstract void enviarAlerta(Paciente paciente, String informacion);

    // Método concreto compartido: log de auditoría
    protected void registrarAuditoria(String tipo, String pacienteId, String canal) {
        System.out.printf("[AUDITORIA] %s | Alerta: %s | Paciente: %s | Canal: %s | ID: %s%n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                tipo, pacienteId, canal, idAlerta);
    }

    // Cambia el canal en tiempo de ejecución (poder del Bridge)
    public void cambiarCanal(CanalNotificacion nuevoCanal) {
        System.out.println("[Bridge] Canal cambiado: " + this.canal.getNombreCanal()
                + " → " + nuevoCanal.getNombreCanal());
        this.canal = nuevoCanal;
    }
}

// ABSTRACCIÓN REFINADA 1: Alerta de Emergencia
class AlertaEmergencia extends AlertaMedica {
    private static final Map<String, String> PROTOCOLOS = new HashMap<>();

    static {
        PROTOCOLOS.put("PARO_CARDIACO", "Activar código azul. RCP inmediato. Desfibrilador en 2 min.");
        PROTOCOLOS.put("TRAUMA_GRAVE", "Activar código rojo. UCI disponible. Hemoderivados listos.");
        PROTOCOLOS.put("ICTUS", "Activar código ictus. TAC urgente. Trombólisis si aplica.");
        PROTOCOLOS.put("SEPSIS", "Protocolo sepsis. Hemocultivos. Antibióticos IV en <1h.");
        PROTOCOLOS.put("DEFAULT", "Evaluación urgente. Médico de guardia inmediato.");
    }

    private final String nivelCriticidad; // CRITICO, ALTO, MEDIO

    public AlertaEmergencia(CanalNotificacion canal, String nivelCriticidad) {
        super(canal);
        this.nivelCriticidad = nivelCriticidad.toUpperCase();
    }

    @Override
    public void enviarAlerta(Paciente paciente, String informacion) {
        // Lógica real: selecciona protocolo, calcula tiempo límite, prioriza canal
        String codigoEmergencia = informacion.toUpperCase().replace(" ", "_");
        String protocolo = PROTOCOLOS.getOrDefault(codigoEmergencia, PROTOCOLOS.get("DEFAULT"));

        // Lógica de criticidad: CRITICO tiene tiempo límite de 5 min
        int tiempoLimiteMin = nivelCriticidad.equals("CRITICO") ? 5 : nivelCriticidad.equals("ALTO") ? 15 : 30;

        // Si el paciente tiene alergias, incluir en la alerta
        String alertaAlergias = paciente.alergias.isEmpty() ? ""
                : "\n⚠️ ALERGIAS CONOCIDAS: " + String.join(", ", paciente.alergias);

        String asunto = "🚨 EMERGENCIA " + nivelCriticidad + " — " + paciente.nombre;
        String cuerpo = String.format(
                "PACIENTE: %s (Edad: %d, Sangre: %s)\n" +
                        "EMERGENCIA: %s\n" +
                        "PROTOCOLO: %s\n" +
                        "TIEMPO LÍMITE: %d minutos%s\n" +
                        "ID Alerta: %s",
                paciente.nombre, paciente.edad, paciente.tipoSangre,
                informacion, protocolo, tiempoLimiteMin, alertaAlergias, idAlerta);

        canal.enviarMensaje(obtenerContacto(paciente), asunto, cuerpo);
        registrarAuditoria("EMERGENCIA-" + nivelCriticidad, paciente.id, canal.getNombreCanal());
    }

    private String obtenerContacto(Paciente paciente) {
        // Simulación: en producción consultaría BD de contactos
        switch (canal.getNombreCanal()) {
            case "Email":
                return "medico.guardia@clinvida.com";
            case "SMS":
                return "+573001234567";
            case "WhatsApp":
                return "+573001234567";
            default:
                return "contacto@clinvida.com";
        }
    }
}

// ABSTRACCIÓN REFINADA 2: Alerta de Cita Médica
class AlertaCita extends AlertaMedica {
    private final int diasAntelacion;

    public AlertaCita(CanalNotificacion canal, int diasAntelacion) {
        super(canal);
        this.diasAntelacion = diasAntelacion;
    }

    @Override
    public void enviarAlerta(Paciente paciente, String informacion) {
        // Lógica: parsear info de cita, calcular fechas, personalizar mensaje
        // informacion formato: "ESPECIALIDAD|DOCTOR|FECHA|HORA|SALA"
        String[] partes = informacion.split("\\|");
        String especialidad = partes.length > 0 ? partes[0] : "Medicina General";
        String doctor = partes.length > 1 ? partes[1] : "Dr. ClinVida";
        String fecha = partes.length > 2 ? partes[2] : LocalDate.now().plusDays(diasAntelacion).toString();
        String hora = partes.length > 3 ? partes[3] : "09:00";
        String sala = partes.length > 4 ? partes[4] : "Consultorios 1er piso";

        // Lógica: calcular días restantes
        long diasRestantes = diasAntelacion;
        String urgenciaMensaje = diasRestantes <= 1 ? "⚡ MAÑANA"
                : diasRestantes <= 3 ? "🔔 En " + diasRestantes + " días" : "📅 En " + diasRestantes + " días";

        // Instrucciones previas según especialidad
        String instrucciones = getInstrucciones(especialidad);

        String asunto = "Recordatorio Cita: " + especialidad + " — " + urgenciaMensaje;
        String cuerpo = String.format(
                "Estimado/a %s,\n\n" +
                        "Le recordamos su cita médica:\n" +
                        "• Especialidad: %s\n" +
                        "• Médico:       %s\n" +
                        "• Fecha:        %s\n" +
                        "• Hora:         %s\n" +
                        "• Ubicación:    %s\n\n" +
                        "PREPARACIÓN PREVIA:\n%s\n\n" +
                        "Para cancelar o reprogramar, comuníquese 24h antes.\n" +
                        "Ref: %s",
                paciente.nombre, especialidad, doctor, fecha, hora, sala, instrucciones, idAlerta);

        canal.enviarMensaje(obtenerContactoPaciente(paciente), asunto, cuerpo);
        registrarAuditoria("CITA-" + especialidad, paciente.id, canal.getNombreCanal());
    }

    private String getInstrucciones(String especialidad) {
        switch (especialidad.toUpperCase()) {
            case "LABORATORIO":
                return "• Ayunas 8 horas\n• Traer orden médica\n• Abundante agua antes";
            case "CARDIOLOGIA":
                return "• No cafeína 24h antes\n• Traer ECGs anteriores\n• Medicamentos habituales";
            case "ODONTOLOGIA":
                return "• Cepillado previo\n• Sin comer 2h antes si sedación";
            default:
                return "• Traer documentos de identidad\n• Carné de salud\n• Exámenes anteriores";
        }
    }

    private String obtenerContactoPaciente(Paciente paciente) {
        switch (canal.getNombreCanal()) {
            case "Email":
                return paciente.id.toLowerCase().replace(" ", ".") + "@email.com";
            case "SMS":
                return "+573009876543";
            case "WhatsApp":
                return "+573009876543";
            default:
                return "paciente@email.com";
        }
    }
}

// ABSTRACCIÓN REFINADA 3: Alerta de Resultados de Exámenes
class AlertaResultados extends AlertaMedica {
    private final boolean requiereAccionInmediata;

    public AlertaResultados(CanalNotificacion canal, boolean requiereAccionInmediata) {
        super(canal);
        this.requiereAccionInmediata = requiereAccionInmediata;
    }

    @Override
    public void enviarAlerta(Paciente paciente, String informacion) {
        // Lógica: parsear resultado, analizar valores críticos, clasificar
        // informacion formato: "EXAMEN|RESULTADO|VALOR_REFERENCIA|UNIDAD"
        String[] partes = informacion.split("\\|");
        String examen = partes.length > 0 ? partes[0] : "Examen General";
        String resultado = partes.length > 1 ? partes[1] : "Pendiente";
        String referencia = partes.length > 2 ? partes[2] : "Normal";
        String unidad = partes.length > 3 ? partes[3] : "";

        // Lógica: determinar si el resultado está fuera de rango
        boolean esCritico = requiereAccionInmediata ||
                resultado.toUpperCase().contains("ALTO") ||
                resultado.toUpperCase().contains("BAJO") ||
                resultado.toUpperCase().contains("POSITIVO");

        String estado = esCritico ? "⚠️ REQUIERE ATENCIÓN" : "✅ DENTRO DEL RANGO";
        String siguiente = esCritico
                ? "Comuníquese con su médico tratante en las próximas 24 horas."
                : "Sus resultados son normales. No requiere acción inmediata.";

        // Nota especial por alergias
        String notaAlergia = "";
        if (esCritico && !paciente.alergias.isEmpty()) {
            notaAlergia = "\n\n⚠️ NOTA: Paciente con alergias registradas: " +
                    String.join(", ", paciente.alergias) +
                    ". Considerar antes de prescribir tratamiento.";
        }

        String asunto = (esCritico ? "⚠️ RESULTADO REQUIERE ATENCIÓN: " : "✅ Resultado disponible: ") + examen;
        String cuerpo = String.format(
                "Paciente: %s | ID: %s\n\n" +
                        "EXAMEN:     %s\n" +
                        "RESULTADO:  %s %s\n" +
                        "REFERENCIA: %s\n" +
                        "ESTADO:     %s\n\n" +
                        "SIGUIENTE PASO:\n%s%s\n\n" +
                        "Para consultar el reporte completo: clinvida.com/resultados/%s",
                paciente.nombre, paciente.id,
                examen, resultado, unidad, referencia,
                estado, siguiente, notaAlergia, idAlerta);

        // Lógica: resultados críticos se envían al médico TAMBIÉN
        if (esCritico) {
            canal.enviarMensaje("medico.tratante@clinvida.com",
                    "RESULTADO CRÍTICO — " + paciente.nombre, cuerpo);
        }
        canal.enviarMensaje(obtenerContactoPaciente(paciente), asunto, cuerpo);
        registrarAuditoria("RESULTADO-" + examen, paciente.id, canal.getNombreCanal());
    }

    private String obtenerContactoPaciente(Paciente paciente) {
        switch (canal.getNombreCanal()) {
            case "Email":
                return "paciente." + paciente.id + "@clinvida.com";
            case "SMS":
                return "+573015554433";
            case "WhatsApp":
                return "+573015554433";
            default:
                return "paciente@email.com";
        }
    }
}

// ───────────────────────────────────────────────────────────────────
// CLASE PRINCIPAL — Orquesta todo el sistema
// ───────────────────────────────────────────────────────────────────
public class Main {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║   ClinVida Medical Center — Sistema de Alertas       ║");
        System.out.println("║   Patrones: ADAPTER + BRIDGE                         ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        // ── Inicializar sistemas legacy ───────────────────────────
        ClinicSoftLegacy clinicSoftLegacy = new ClinicSoftLegacy();
        MedProDB medProLegacy = new MedProDB();
        HospitalAPILegacy hospitalAPILegacy = new HospitalAPILegacy();

        // ── Crear adapters (PATRÓN ADAPTER) ──────────────────────
        PasarelaMedica sistemaClinVida = new ClinicSoftAdapter(clinicSoftLegacy);
        PasarelaMedica sistemaEPS = new MedProAdapter(medProLegacy);
        PasarelaMedica sistemaEstatal = new HospitalAPIAdapter(hospitalAPILegacy);

        // ── Inicializar canales de notificación ───────────────────
        CanalEmail email = new CanalEmail();
        CanalSMS sms = new CanalSMS();
        CanalWhatsApp whatsapp = new CanalWhatsApp();

        System.out.println("\n══ DEMO 1: ADAPTER — Buscar pacientes en sistemas distintos ══");
        Paciente p1 = sistemaClinVida.buscarPaciente("CS-1001");
        Paciente p2 = sistemaEPS.buscarPaciente("MP-CC-1090432178");
        Paciente p3 = sistemaEstatal.buscarPaciente("HA-UUID-D4E5F6");
        System.out.println("ClinicSoft: " + p1);
        System.out.println("MedPro:     " + p2);
        System.out.println("HospitalAPI:" + p3);

        System.out.println("\n══ DEMO 2: ADAPTER — Registrar consultas en sistemas legacy ══");
        sistemaClinVida.registrarConsulta("CS-1003", "Hipertensión estadio II controlada", "Dr. Salcedo");
        sistemaEPS.registrarConsulta("MP-CC-8801234567", "Control artritis reumatoide", "Dr. Mora");
        sistemaEstatal.registrarConsulta("HA-UUID-A1B2C3", "Chequeo anual — sin hallazgos", "Dr. Lozano");

        System.out.println("\n══ DEMO 3: ADAPTER — Calcular costos con lógica de negocio ══");
        double costoCS = sistemaClinVida.calcularCosto("CS-1003", "URGENCIAS");
        double costoMP = sistemaEPS.calcularCosto("MP-CC-8801234567", "LABORATORIO");
        double costoHA = sistemaEstatal.calcularCosto("HA-UUID-D4E5F6", "CONSULTA");
        System.out.printf("ClinicSoft (CS-1003)  URGENCIAS:   $%.2f COP%n", costoCS);
        System.out.printf("MedPro     (Suárez)   LABORATORIO: $%.2f COP%n", costoMP);
        System.out.printf("HospitalAPI(D4E5F6)   CONSULTA:    $%.2f COP%n", costoHA);

        System.out.println("\n══ DEMO 4: BRIDGE — Alerta Emergencia por Email ══");
        AlertaMedica alerta1 = new AlertaEmergencia(email, "CRITICO");
        alerta1.enviarAlerta(p1, "PARO_CARDIACO");

        System.out.println("\n══ DEMO 5: BRIDGE — Alerta Emergencia por SMS ══");
        AlertaMedica alerta2 = new AlertaEmergencia(sms, "ALTO");
        alerta2.enviarAlerta(p2, "SEPSIS");

        System.out.println("\n══ DEMO 6: BRIDGE — Alerta Cita por WhatsApp ══");
        AlertaMedica alerta3 = new AlertaCita(whatsapp, 2);
        alerta3.enviarAlerta(p3, "CARDIOLOGIA|Dr. Salcedo|2026-03-15|10:30|Piso 3 - Sala 302");

        System.out.println("\n══ DEMO 7: BRIDGE — Alerta Resultados críticos por Email ══");
        AlertaMedica alerta4 = new AlertaResultados(email, true);
        alerta4.enviarAlerta(p2, "GLUCOSA|320 mg/dL (ALTO)|70-100 mg/dL|mg/dL");

        System.out.println("\n══ DEMO 8: BRIDGE — Cambio de canal EN TIEMPO DE EJECUCIÓN ══");
        System.out.println("Situación: SMS falla, cambiamos a WhatsApp automáticamente");
        AlertaMedica alerta5 = new AlertaCita(sms, 1);
        alerta5.enviarAlerta(p1, "LABORATORIO|Dr. Torres|2026-03-14|07:00|Lab Central");
        alerta5.cambiarCanal(whatsapp); // ← PODER DEL BRIDGE
        alerta5.enviarAlerta(p1, "LABORATORIO|Dr. Torres|2026-03-14|07:00|Lab Central");

        System.out.println("\n══ RESUMEN FINAL ══");
        System.out.println("ADAPTER: 3 sistemas legacy → 1 interfaz unificada PasarelaMedica");
        System.out.println("BRIDGE:  3 tipos de alerta × 3 canales = combinaciones sin explosión de clases");
        System.out.println("SMS enviados: " + sms.getSmsSentCount());
        System.out.println("Emails en cola: " + email.getMensajesEnviados().size());
    }
}
