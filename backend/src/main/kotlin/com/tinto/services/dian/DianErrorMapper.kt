package com.tinto.services.dian

import org.springframework.stereotype.Component

/**
 * Maps DIAN error codes and messages to user-friendly guidance.
 *
 * DIAN returns technical error codes and Spanish-language messages.
 * This mapper translates those into actionable steps the user can take
 * to fix and resubmit their invoice.
 */
@Component
class DianErrorMapper {

    /**
     * Map a DIAN error code and message to user guidance
     *
     * @param errorCode DIAN error code (e.g., "04", "99")
     * @param errorMessage Raw error message from DIAN
     * @return ErrorGuidance with category, explanation, and suggested actions
     */
    fun mapToGuidance(errorCode: String?, errorMessage: String?): ErrorGuidance {
        val code = errorCode ?: "UNKNOWN"
        val message = errorMessage ?: "Error desconocido"

        // Match by error code first, then by message pattern
        val category = categorizeError(code, message)
        val explanation = explainError(category, message)
        val actions = suggestActions(category, message)
        val retryable = isRetryable(category)

        return ErrorGuidance(
            category = category,
            originalCode = code,
            originalMessage = message,
            explanation = explanation,
            suggestedActions = actions,
            retryable = retryable
        )
    }

    private fun categorizeError(code: String, message: String): ErrorCategory {
        // DIAN status codes
        return when (code) {
            "04" -> categorizeByMessage(message)
            "99" -> ErrorCategory.DIAN_SERVICE_ERROR
            else -> categorizeByMessage(message)
        }
    }

    private fun categorizeByMessage(message: String): ErrorCategory {
        val lowerMsg = message.lowercase()
        return when {
            // XML / UBL structure errors
            lowerMsg.contains("xml") || lowerMsg.contains("estructura") ||
            lowerMsg.contains("schema") || lowerMsg.contains("ubl") ->
                ErrorCategory.XML_STRUCTURE

            // Digital signature errors
            lowerMsg.contains("firma") || lowerMsg.contains("certificado") ||
            lowerMsg.contains("signature") || lowerMsg.contains("xades") ->
                ErrorCategory.SIGNATURE

            // CUFE / hash errors
            lowerMsg.contains("cufe") || lowerMsg.contains("cude") ||
            lowerMsg.contains("hash") ->
                ErrorCategory.CUFE_MISMATCH

            // NIT / identification errors
            lowerMsg.contains("nit") || lowerMsg.contains("identificaci") ||
            lowerMsg.contains("contribuyente") ->
                ErrorCategory.IDENTIFICATION

            // Numbering / sequence errors
            lowerMsg.contains("numeraci") || lowerMsg.contains("secuencia") ||
            lowerMsg.contains("consecutivo") || lowerMsg.contains("prefijo") ||
            lowerMsg.contains("rango") ->
                ErrorCategory.NUMBERING

            // Tax / amount calculation errors
            lowerMsg.contains("impuesto") || lowerMsg.contains("iva") ||
            lowerMsg.contains("total") || lowerMsg.contains("valor") ||
            lowerMsg.contains("monto") || lowerMsg.contains("calcul") ->
                ErrorCategory.TAX_CALCULATION

            // Date / time errors
            lowerMsg.contains("fecha") || lowerMsg.contains("hora") ||
            lowerMsg.contains("vencimiento") || lowerMsg.contains("emisi") ->
                ErrorCategory.DATE_TIME

            // Duplicate invoice
            lowerMsg.contains("duplicad") || lowerMsg.contains("ya existe") ||
            lowerMsg.contains("already") ->
                ErrorCategory.DUPLICATE

            // Authorization / permission errors
            lowerMsg.contains("autoriz") || lowerMsg.contains("permiso") ||
            lowerMsg.contains("habilitaci") || lowerMsg.contains("acceso") ->
                ErrorCategory.AUTHORIZATION

            // Connection / timeout
            lowerMsg.contains("timeout") || lowerMsg.contains("conexi") ||
            lowerMsg.contains("servicio") ->
                ErrorCategory.DIAN_SERVICE_ERROR

            else -> ErrorCategory.UNKNOWN
        }
    }

    private fun explainError(category: ErrorCategory, originalMessage: String): String {
        return when (category) {
            ErrorCategory.XML_STRUCTURE ->
                "El documento XML no cumple con la estructura UBL 2.1 requerida por la DIAN. " +
                "Esto suele indicar campos faltantes o mal formateados en la factura."

            ErrorCategory.SIGNATURE ->
                "Hay un problema con la firma digital XAdES-EPES del documento. " +
                "Esto puede deberse a un certificado expirado, invalido, o a un error en el proceso de firma."

            ErrorCategory.CUFE_MISMATCH ->
                "El codigo CUFE/CUDE calculado no coincide con los datos de la factura. " +
                "Esto indica una inconsistencia entre los datos de la factura y el hash generado."

            ErrorCategory.IDENTIFICATION ->
                "Hay un error en los datos de identificacion (NIT del emisor o del receptor). " +
                "Verifique que los NITs esten correctos y registrados ante la DIAN."

            ErrorCategory.NUMBERING ->
                "El numero de factura, prefijo o rango de numeracion no es valido. " +
                "Verifique que la numeracion este dentro del rango autorizado por la DIAN."

            ErrorCategory.TAX_CALCULATION ->
                "Los calculos de impuestos o totales no son correctos. " +
                "Verifique que los subtotales, IVA y total coincidan con los items."

            ErrorCategory.DATE_TIME ->
                "La fecha u hora de emision no es valida. " +
                "La DIAN requiere que la fecha este dentro de un rango permitido."

            ErrorCategory.DUPLICATE ->
                "Ya existe una factura con el mismo numero y prefijo. " +
                "Cada factura debe tener un numero unico dentro del prefijo asignado."

            ErrorCategory.AUTHORIZATION ->
                "No tiene autorizacion para emitir facturas electronicas. " +
                "Verifique su estado de habilitacion ante la DIAN."

            ErrorCategory.DIAN_SERVICE_ERROR ->
                "El servicio de la DIAN presento un error temporal. " +
                "Esto suele resolverse al reintentar en unos minutos."

            ErrorCategory.UNKNOWN ->
                "Se presento un error no clasificado: $originalMessage"
        }
    }

    private fun suggestActions(category: ErrorCategory, originalMessage: String): List<String> {
        return when (category) {
            ErrorCategory.XML_STRUCTURE -> listOf(
                "Verifique que todos los campos obligatorios de la factura esten completos",
                "Revise el formato de los campos (fechas, montos, codigos)",
                "Contacte soporte tecnico si el error persiste"
            )
            ErrorCategory.SIGNATURE -> listOf(
                "Verifique que el certificado digital no haya expirado",
                "Confirme que el certificado corresponde al NIT del emisor",
                "Reintente el proceso de firma",
                "Si usa certificado .p12, verifique que la contrasena sea correcta"
            )
            ErrorCategory.CUFE_MISMATCH -> listOf(
                "Reintente la emision (el CUFE se recalculara automaticamente)",
                "Verifique que no haya modificado datos de la factura despues de la firma"
            )
            ErrorCategory.IDENTIFICATION -> listOf(
                "Verifique el NIT del emisor en la configuracion",
                "Confirme la identificacion del cliente",
                "Verifique que ambos esten registrados ante la DIAN"
            )
            ErrorCategory.NUMBERING -> listOf(
                "Verifique el rango de numeracion autorizado por la DIAN",
                "Confirme que el prefijo coincide con la resolucion",
                "Si el rango esta agotado, solicite nueva resolucion ante la DIAN"
            )
            ErrorCategory.TAX_CALCULATION -> listOf(
                "Revise los calculos de cada linea (cantidad x precio unitario)",
                "Verifique que la tasa de IVA aplicada sea correcta",
                "Confirme que subtotal + impuestos = total"
            )
            ErrorCategory.DATE_TIME -> listOf(
                "Verifique que la fecha de emision sea la fecha actual",
                "La DIAN permite un margen de hasta 10 dias antes de la fecha actual",
                "Ajuste la fecha y reintente"
            )
            ErrorCategory.DUPLICATE -> listOf(
                "Use un numero de factura diferente",
                "Verifique si la factura anterior fue procesada correctamente",
                "Si fue rechazada, corrija y reenvie con el mismo numero"
            )
            ErrorCategory.AUTHORIZATION -> listOf(
                "Verifique su estado de habilitacion en el portal de la DIAN",
                "Confirme las credenciales de acceso al servicio",
                "Contacte a la DIAN si su habilitacion ha sido revocada"
            )
            ErrorCategory.DIAN_SERVICE_ERROR -> listOf(
                "Espere unos minutos y reintente",
                "Verifique el estado del servicio de la DIAN",
                "Si persiste por mas de 1 hora, reporte a la DIAN"
            )
            ErrorCategory.UNKNOWN -> listOf(
                "Revise el mensaje de error original: $originalMessage",
                "Contacte soporte tecnico con el codigo de error"
            )
        }
    }

    private fun isRetryable(category: ErrorCategory): Boolean {
        return when (category) {
            ErrorCategory.DIAN_SERVICE_ERROR -> true
            ErrorCategory.CUFE_MISMATCH -> true
            ErrorCategory.SIGNATURE -> true
            else -> false
        }
    }

    /**
     * Error categories for DIAN rejections
     */
    enum class ErrorCategory {
        XML_STRUCTURE,
        SIGNATURE,
        CUFE_MISMATCH,
        IDENTIFICATION,
        NUMBERING,
        TAX_CALCULATION,
        DATE_TIME,
        DUPLICATE,
        AUTHORIZATION,
        DIAN_SERVICE_ERROR,
        UNKNOWN
    }

    /**
     * User-friendly error guidance
     */
    data class ErrorGuidance(
        val category: ErrorCategory,
        val originalCode: String,
        val originalMessage: String,
        val explanation: String,
        val suggestedActions: List<String>,
        val retryable: Boolean
    )
}
