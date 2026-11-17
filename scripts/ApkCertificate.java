import com.android.apksig.ApkVerifier;
import java.io.File;
import java.security.MessageDigest;
import java.util.HexFormat;

class ApkCertificate {
    public static void main(String[] args) throws Exception {
        var apk = new File(args[0]);
        var result = new ApkVerifier.Builder(apk).build().verify();
        if (!result.isVerified() || result.getSignerCertificates().size() != 1) {
            throw new IllegalStateException(
                apk.getName() + ": se așteaptă un APK valid cu un singur certificat. " + result.getAllErrors());
        }
        var certificate = result.getSignerCertificates().get(0).getEncoded();
        var digest = MessageDigest.getInstance("SHA-256").digest(certificate);
        System.out.println(HexFormat.of().formatHex(digest));
    }
}
