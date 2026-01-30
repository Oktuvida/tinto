
# Especificación Técnica de Implementación: Facturación Electrónica DIAN (V1.9)

**Documento Objetivo:** Desarrollo de Cliente de Facturación / Middleware ("Port")
**Estándar Base:** UBL 2.1 (Universal Business Language)
**Protocolo de Transporte:** SOAP 1.2 / HTTPS
**Seguridad:** X.509, WS-Security, XAdES-EPES

---

## 1. Configuración del Entorno y Constantes

Antes de iniciar el flujo, el sistema debe tener configurados los siguientes parámetros obtenidos del proceso de Habilitación en el portal DIAN:

* **SoftwareID:** Identificador único del software (GUID).
* **SoftwarePIN:** Código de seguridad asociado al software.
* **Certificado Digital:** Archivo `.p12` o `.pfx` (emitido por ONAC) con su clave privada.
* **Clave Técnica (TechnicalKey):** Clave entregada por la DIAN (solo para Producción) asociada a los rangos de numeración. En Habilitación es estática (ver set de pruebas).
* **Endpoints (WSDL):**
* *Habilitación:* `https://vpfe-hab.dian.gov.co/WcfDianCustomerServices.svc?wsdl`
* *Producción:* `https://vpfe.dian.gov.co/WcfDianCustomerServices.svc?wsdl`



---

## 2. Fase I: Construcción del XML (UBL 2.1)

El archivo XML debe seguir la estructura estricta definida por los XSD de la DIAN.

### 2.1. Cálculo del CUFE (Código Único de Factura Electrónica)

**Crítico:** Este valor debe calcularse **antes** de finalizar el XML, ya que se incluye dentro del mismo.

**Algoritmo:** SHA-384
**Input (Concatenación sin espacios):**

```text
NumFac + FecFac + ValFac + CodImp1 + ValImp1 + CodImp2 + ValImp2 + ValImp3 + ValImp3 + ValTot + NitOfe + DocAdq + ClaveTecnica + TipoAmbiente

```

> *Nota:* Si un impuesto no aplica, no se concatena. Los valores numéricos deben ir sin puntos de miles, punto decimal exacto (2 decimales).

### 2.2. Estructura Esqueleto del XML (`Invoice`)

El XML debe contener los siguientes nodos mandatorios con los Namespaces correctos (`urn:oasis:names:specification:ubl:schema:xsd:Invoice-2`, etc.):

1. **UBLExtensions:**
* Aquí irá la **Firma Digital** (vacía inicialmente).
* Aquí van datos propios de la DIAN (Información del proveedor tecnológico, si aplica).


2. **CBC Header:**
* `UBLVersionID`: "UBL 2.1"
* `CustomizationID`: "10" (para factura de venta estándar).
* `ProfileID`: "DIAN 2.1: Factura Electrónica de Venta"
* `ID`: Prefijo + Número (Ej: SETT1).
* `UUID`: El **CUFE** calculado en el paso 2.1 (schemeName="CUFE-SHA384").
* `IssueDate` / `IssueTime`: Fecha y hora de generación (Hora Colombia -05:00).


3. **AccountingSupplierParty (Emisor):**
* Datos completos de la empresa, NIT (con DV), Régimen Fiscal (Lista de valores 13.2.7.6 del anexo).


4. **AccountingCustomerParty (Receptor):**
* NIT/Cédula, Nombre, Correo Electrónico (Vital para la entrega gráfica).


5. **PaymentMeans:**
* ID: 1 (Contado) o 2 (Crédito).
* PaymentMeansCode: (Ej: 10 para Efectivo, 41 para Transferencia).


6. **TaxTotal (Impuestos Globales):**
* Sumatoria de IVAs e INC.
* **Importante:** La DIAN valida matemáticamente que la suma de líneas coincida con este total.


7. **LegalMonetaryTotal:**
* `LineExtensionAmount`: Subtotal antes de impuestos.
* `TaxExclusiveAmount`: Base gravable.
* `TaxInclusiveAmount`: Total con impuestos.
* `PayableAmount`: Total a pagar.


8. **InvoiceLine (Detalle):**
* Item por item. Debe incluir `StandardItemIdentification` (Estándar de codificación) y `Price`.



### 2.3. Generación del QR (Campo `QRCode`)

Se debe generar una cadena de texto (URL) que va dentro de un nodo `ExtensionContent` específico.
**Estructura:**
`https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey={CUFE}&date={Fecha}&time={Hora}`

---

## 3. Fase II: Firma Digital (XAdES-EPES)

Una vez construido el XML en memoria, se procede a firmarlo. Este es el punto de fallo técnico más común.

**Requisitos de la Firma:**

* **Algoritmo de Firma:** `http://www.w3.org/2001/04/xmldsig-more#rsa-sha256`
* **Algoritmo de Canonicalización:** `http://www.w3.org/TR/2001/REC-xml-c14n-20010315`
* **Tipo:** **Enveloped Signature** (La firma queda dentro del nodo `UBLExtension` del XML).

**Pasos Técnicos:**

1. Serializar el XML (Canonicalizar).
2. Calcular el *Digest* (Hash SHA-256) del documento completo.
3. Crear el nodo `SignedInfo` referenciando el ID del objeto.
4. Firmar el `SignedInfo` usando la **Llave Privada** del certificado.
5. Insertar el certificado público (X.509) en el nodo `KeyInfo` (Base64).
6. Insertar el bloque `<ds:Signature>` completo en el nodo `UBLExtensions` reservado.

---

## 4. Fase III: Empaquetado y Transporte

La DIAN no recibe el XML crudo.

1. **Compresión:** El archivo XML firmado (ej: `face_f001.xml`) se comprime en formato **ZIP**.
* Nombre del ZIP: `z{NIT_SIN_DV}{CODIGO_DOC}{AÑO}{CONSECUTIVO}.zip` (La nomenclatura exacta está en la sección de nombres de archivo del anexo).


2. **SOAP Envelope:** Construir la petición para el servicio `SendBillAsync`.

**Estructura SOAP Request:**

```xml
<soapenv:Envelope ...>
   <soapenv:Header>
      <wsa:Action>http://wcf.dian.colombia/IWcfDianCustomerServices/SendBillAsync</wsa:Action>
      <wsa:To>https://vpfe.dian.gov.co/WcfDianCustomerServices.svc</wsa:To>
      <wsse:Security>
          <wsse:UsernameToken>
              <wsse:Username>{SoftwareID}</wsse:Username>
              <wsse:Password Type="...#PasswordText">{SHA256(SoftwarePIN + SoftwareID)}</wsse:Password>
              <wsse:Nonce>...</wsse:Nonce>
              <wsu:Created>...</wsu:Created>
          </wsse:UsernameToken>
      </wsse:Security>
   </soapenv:Header>
   <soapenv:Body>
      <rep:SendBillAsync>
         <rep:fileName>{NombreDelZip}</rep:fileName>
         <rep:contentFile>{Base64_Del_ZIP}</rep:contentFile>
      </rep:SendBillAsync>
   </soapenv:Body>
</soapenv:Envelope>

```

---

## 5. Fase IV: Respuesta y Validación (Modelo Asíncrono)

Al enviar `SendBillAsync`, la DIAN **NO** dice si la factura es válida inmediatamente.

### Paso 1: Recepción del TrackId

El servicio responde con un objeto `UploadDocumentResponse`:

* `ZipKey`: Hash del recibido.
* **`ErrorMessageList`:** Si hay errores estructurales (XML mal formado, ZIP corrupto), aparecen aquí y el proceso muere.
* **`TrackId`:** Si todo está bien estructuralmente, retorna un UUID. **Guardar este ID.**

### Paso 2: Polling (Consulta de Estado)

Se debe invocar el método `GetStatusZip` repetidamente (espera recomendada: 3 a 5 segundos).

* **Input:** `TrackId`.
* **Output (`DianResponse`):**
* `IsValid`: `true` / `false`.
* `StatusMessage`: Mensaje legible.
* `XmlBase64Bytes`: **IMPORTANTE.** Aquí viene el `ApplicationResponse`.



### Paso 3: Procesar el ApplicationResponse

Se debe decodificar el `XmlBase64Bytes` (es un ZIP que contiene un XML).
Este XML es el "Acuse de Recibo Oficial".

* Buscar el nodo `cac:DocumentResponse / cac:Response / cbc:ResponseCode`.
* **Código `02`:** Documento validado exitosamente. (Factura Legal).
* **Código `04`:** Rechazado. (Ver lista de errores adjunta en el XML).

---

## 6. Manejo de Errores Comunes (Troubleshooting)

Para el desarrollo del "Port", tener en cuenta:

1. **Regla 90 (Valores matemáticos):** La DIAN recalcula todo. Si `(Precio * Cantidad) != TotalLinea`, rechaza. Tolerancia de pocos pesos por redondeo, pero es estricta.
2. **Fechas:** La fecha de emisión en el XML no puede diferir en más de 5 o 10 días (según la regla vigente) de la fecha actual de transmisión.
3. **Codificación:** Asegurar UTF-8. Caracteres especiales (`ñ`, `&`) en nombres de empresas rompen la firma si no se escapan correctamente en XML (`&amp;`).

## 7. Entregable Final al Cliente (Graphic Representation)

Aunque el anexo es técnico sobre XML, exige que se entregue al cliente:

1. El XML Firmado (`AttachedDocument`).
2. El `ApplicationResponse` (Validation).
3. Representación Gráfica (PDF) que **DEBE** incluir el código QR generado y el CUFE visible.