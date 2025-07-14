// Import necessary Java libraries for encoding, cryptography, and utilities
import java.nio.charset.StandardCharsets; // For UTF-8 charset handling
import java.security.MessageDigest; // For SHA-256 hashing
import java.security.SecureRandom; // For generating secure random IVs
import java.util.Arrays; // For array operations like sorting
import java.util.Base64; // For Base64 encoding/decoding
import javax.crypto.Cipher; // For encryption/decryption operations
import javax.crypto.SecretKeySpec; // For creating AES keys
import javax.crypto.spec.GCMParameterSpec; // For GCM mode parameters
import javax.crypto.AEADBadTagException; // For handling authentication failures in GCM

// Define the public class for encrypted multisig descriptors
public class EncryptedMultisigDescriptor {

    // Constants for IV and authentication tag lengths in GCM mode
    private static final int IV_LENGTH = 12; // Standard IV size for AES-GCM
    private static final int TAG_LENGTH = 16; // Standard tag size for authentication

    // Method to generate a symmetric key from two zpubs by hashing their sorted concatenation
    public static byte[] getPairKey(String zpub1, String zpub2) throws Exception {
        String[] pair = {zpub1, zpub2}; // Create array of the two zpubs
        Arrays.sort(pair); // Sort to ensure consistent order regardless of input
        String concat = pair[0] + pair[1]; // Concatenate sorted zpubs
        MessageDigest md = MessageDigest.getInstance("SHA-256"); // Get SHA-256 digest instance
        return md.digest(concat.getBytes(StandardCharsets.UTF_8)); // Hash concatenation and return bytes
    }

    // Method to encrypt a descriptor string using AES-GCM with the given key
    public static String encrypt(String descriptor, byte[] key) throws Exception {
        SecretKeySpec skey = new SecretKeySpec(key, "AES"); // Create AES key from byte array
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); // Get AES-GCM cipher instance
        byte[] iv = new byte[IV_LENGTH]; // Create IV array
        new SecureRandom().nextBytes(iv); // Fill IV with secure random bytes
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH * 8, iv); // Create GCM spec with IV and tag bits
        cipher.init(Cipher.ENCRYPT_MODE, skey, spec); // Initialize cipher for encryption
        byte[] ct = cipher.doFinal(descriptor.getBytes(StandardCharsets.UTF_8)); // Encrypt descriptor to ciphertext

        // Build encryption blob: IV + Ciphertext (tag included in ciphertext)
        byte[] blob = new byte[IV_LENGTH + ct.length]; // IV + Ciphertext (which includes tag)
        System.arraycopy(iv, 0, blob, 0, IV_LENGTH); // Copy IV to start of blob
        System.arraycopy(ct, 0, blob, IV_LENGTH, ct.length); // Copy entire ciphertext (tag included) after IV

        return Base64.getEncoder().encodeToString(blob); // Base64 encode blob and return as string
    }

    // Method to decrypt an encrypted blob string using AES-GCM with the given key
    public static String decrypt(String blobStr, byte[] key) throws Exception {
        byte[] blob = Base64.getDecoder().decode(blobStr); // Decode Base64 blob to bytes
        if (blob.length < IV_LENGTH) throw new IllegalArgumentException("Invalid blob length"); // Check minimum length

        byte[] iv = Arrays.copyOfRange(blob, 0, IV_LENGTH); // Extract IV from blob
        byte[] ct = Arrays.copyOfRange(blob, IV_LENGTH, blob.length); // Extract ciphertext (tag included)

        SecretKeySpec skey = new SecretKeySpec(key, "AES"); // Create AES key
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); // Get cipher instance
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH * 8, iv); // Create GCM spec
        cipher.init(Cipher.DECRYPT_MODE, skey, spec); // Initialize for decryption

        try {
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8); // Decrypt and return string
        } catch (AEADBadTagException e) {
            return null; // Failed decryption due to tag mismatch
        }
    }

    // Comment block: Example usage for encryption and decryption in a 2-of-3 setup
    // Usage example: String[] zpubs = {...}; String descriptor = "...";
    // String[] blobs = new String[3];
    // blobs[0] = encrypt(descriptor, getPairKey(zpubs[0], zpubs[1]));
    // etc.
    // To decrypt: given zpubA, zpubB, try decrypt each blob with getPairKey(A,B), return first non-null.
}
