package com.tinto.security

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties
import org.jasypt.encryption.StringEncryptor
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for secure loading of encrypted properties and secrets.
 *
 * All sensitive configuration values should be encrypted using Jasypt.
 * Format in application.properties: password=ENC(encrypted_value)
 *
 * The encryption password is provided via environment variable:
 * JASYPT_ENCRYPTOR_PASSWORD
 *
 * To encrypt a value for use in properties:
 * java -cp jasypt.jar org.jasypt.intf.cli.JasyptPBEStringEncryptionCLI \
 *   input="mysecret" password="$JASYPT_ENCRYPTOR_PASSWORD" algorithm=PBEWITHHMACSHA512ANDAES_256
 */
@Configuration
@EnableEncryptableProperties
class SecretsConfig {

    @Value("\${jasypt.encryptor.password:#{null}}")
    private val encryptorPassword: String? = null

    /**
     * Configure Jasypt String Encryptor for encrypting/decrypting properties
     */
    @Bean("jasyptStringEncryptor")
    fun stringEncryptor(): StringEncryptor {
        val encryptor = PooledPBEStringEncryptor()
        val config = SimpleStringPBEConfig()
        
        // Get encryption password from environment variable
        val password = encryptorPassword 
            ?: System.getenv("JASYPT_ENCRYPTOR_PASSWORD")
            ?: throw IllegalStateException(
                "Jasypt encryption password not configured. " +
                "Set JASYPT_ENCRYPTOR_PASSWORD environment variable."
            )
        
        config.password = password
        config.algorithm = "PBEWITHHMACSHA512ANDAES_256"
        config.keyObtentionIterations = "10000"
        config.poolSize = "1"
        config.providerName = "BC" // BouncyCastle provider
        config.saltGeneratorClassName = "org.jasypt.salt.RandomSaltGenerator"
        config.ivGeneratorClassName = "org.jasypt.iv.RandomIvGenerator"
        config.stringOutputType = "base64"
        
        encryptor.setConfig(config)
        return encryptor
    }
}

/**
 * Data class for DIAN configuration
 */
data class DianConfig(
    val environment: String,
    val wsdlUrl: String,
    val username: String?,
    val password: String?,
    val timeout: Int = 30000
)

/**
 * Service for loading and managing encrypted configuration
 */
@Configuration
class ConfigurationService(
    @Value("\${dian.environment:habilitacion}")
    private val dianEnvironment: String,
    
    @Value("\${dian.habilitacion.wsdl}")
    private val habilitacionWsdl: String,
    
    @Value("\${dian.produccion.wsdl}")
    private val produccionWsdl: String,
    
    @Value("\${dian.username:#{null}}")
    private val dianUsername: String?,
    
    @Value("\${dian.password:#{null}}")
    private val dianPassword: String?, // Will be auto-decrypted if ENC()
    
    @Value("\${tinto.master-key.location:/var/tinto/master.key.enc}")
    val masterKeyLocation: String,
    
    @Value("\${tinto.encryption.algorithm:AES-256-GCM}")
    val encryptionAlgorithm: String
) {

    /**
     * Get DIAN configuration for current environment
     */
    fun getDianConfig(): DianConfig {
        val wsdl = when (dianEnvironment) {
            "produccion" -> produccionWsdl
            else -> habilitacionWsdl
        }
        
        return DianConfig(
            environment = dianEnvironment,
            wsdlUrl = wsdl,
            username = dianUsername,
            password = dianPassword
        )
    }

    /**
     * Check if running in production environment
     */
    fun isProduction(): Boolean = dianEnvironment == "produccion"

    /**
     * Check if running in staging environment
     */
    fun isStaging(): Boolean = dianEnvironment == "staging"

    /**
     * Check if running in dev/habilitacion environment
     */
    fun isDevelopment(): Boolean = dianEnvironment == "habilitacion" || dianEnvironment == "dev"
}
