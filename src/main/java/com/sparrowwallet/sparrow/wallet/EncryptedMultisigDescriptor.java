// Import necessary Java libraries for encoding, cryptography, and utilities
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKeySpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.AEADBadTagException;

// Define the public class for encrypted multisig descriptors
public class EncryptedMultisigDescriptor {

    // Constants for IV and authentication tag lengths in GCM mode
    private static final int IV_LENGTH = 12; // Standard IV size for AES-GCM
    private static final int TAG_LENGTH = 16; // Standard tag size for authentication

    // Method to generate a symmetric key from two zpubs by hashing their sorted concatenation
    public static byte[] getPairKey(String zpub1, String zpub2) throws Exception {
        if (zpub1 == null || zpub2 == null || zpub1.isEmpty() || zpub2.isEmpty()) {
            throw new IllegalArgumentException("zpubs cannot be null or empty");
        }
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

    // Generate all 2-of-3 combinations of zpubs
    public static List<String[]> generatePairs(String[] zpubs) {
        if (zpubs.length != 3) {
            throw new IllegalArgumentException("Must provide exactly 3 zpubs");
        }

        // List to store combinations of pairs
        List<String[]> pairs = new ArrayList<>();
        pairs.add(new String[]{zpubs[0], zpubs[1]});
        pairs.add(new String[]{zpubs[0], zpubs[2]});
        pairs.add(new String[]{zpubs[1], zpubs[2]});

        return pairs;
    }

    // Encrypt a descriptor string using 2-of-3 scheme
    public static List<String> encrypt2of3(String descriptor, String[] zpubs) throws Exception {
        // Generate 2-of-3 pairs
        List<String[]> pairs = generatePairs(zpubs);

        // List to store the encrypted blobs
        List<String> blobs = new ArrayList<>();

        // Encrypt using each pair
        for (String[] pair : pairs) {
            byte[] key = getPairKey(pair[0], pair[1]); // Generate symmetric key for the pair
            String blob = encrypt(descriptor, key);    // Encrypt using that key
            blobs.add(blob);
        }

        return blobs; // Return list of encrypted blobs
    }

    // Decrypt an encrypted blob using 2-of-3 scheme
    public static String decrypt2of3(String blob, String[] zpubs) throws Exception {
        // Generate 2-of-3 pairs
        List<String[]> pairs = generatePairs(zpubs);

        // Try decrypting using each pair
        for (String[] pair : pairs) {
            byte[] key = getPairKey(pair[0], pair[1]); // Generate symmetric key for the pair
            String descriptor = decrypt(blob, key);   // Attempt to decrypt

            if (descriptor != null) { // If successful, return the descriptor
                return descriptor;
            }
        }

        // If no pair works, throw an exception
        throw new SecurityException("Failed to decrypt blob with given zpubs");
    }

    // Example flow for encryption and decryption of 2-of-3
    public static void twoOfThreeFlow(String[] zpubs, String descriptor) throws Exception {
        // 1. Encrypt descriptor using 2-of-3 scheme
        List<String> blobs = encrypt2of3(descriptor, zpubs);
        System.out.println("Encrypted blobs:");
        for (String blob : blobs) {
            System.out.println(blob);
        }

        // 2. Attempt decryption using any valid pair
        System.out.println("\nAttempting decryption:");
        String result = decrypt2of3(blobs.get(0), zpubs); // Test with the first blob
        System.out.println("Decrypted result: " + result);
    }
}
