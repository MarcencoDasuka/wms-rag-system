package com.isd.wms.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class Def02SeedCredentialsRemediationTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // Exactly 17 users and plaintext passwords leaked in V31 comments
    private static final Map<String, String> LEAKED_CREDENTIALS = new LinkedHashMap<>() {{
        put("michael.scott", "Office%13");
        put("jim.halpert", "Dunder@38");
        put("pam.beesly", "Office!96");
        put("dwight.schrute", "WorldsBest#64");
        put("stanley.hudson", "America$21");
        put("kevin.malone", "Dunder#74");
        put("angela.martin", "BestBoss*35");
        put("oscar.martinez", "Dunder%67");
        put("phyllis.vance", "America*30");
        put("kelly.kapoor", "Scranton@45");
        put("toby.flenderson", "Dunder!53");
        put("creed.bratton", "Office!58");
        put("meredith.palmer", "Scranton%54");
        put("ryan.howard", "America!68");
        put("darryl.philbin", "TheOffice%20");
        put("holly.flax", "WorldsBest$56");
        put("jan.levinson", "Office$15");
    }};

    // The original hashes from V31__seed_warehouse_data_final.sql
    private static final Map<String, String> V31_HASHES = new LinkedHashMap<>() {{
        put("michael.scott", "$2b$12$MfIbBa1ccvzcHJ7qkgxoxONeDzhSj3VN.N6GoyrukbYaFOZKomfcm");
        put("jim.halpert", "$2b$12$lddj0PHxU3qWL2NV1KlCGuN/sLJ24YWQlm.On2USkocqlhZCwhY1i");
        put("pam.beesly", "$2b$12$Sj0ztRyzCYVomhTmBWCVVu6HGwpoKq2Qr/XlM12gr50VD2.8UudiG");
        put("dwight.schrute", "$2b$12$Xj4YCVOer/emSh67yuxFv.ThmWDK921ior6K0MASu1yTq9trMqUOq");
        put("stanley.hudson", "$2b$12$EUIHWo4v/atF1LdCHHXxf.c/G9nmvfNdJrrfK48Q89wPcmYC1oLIS");
        put("kevin.malone", "$2b$12$I8ccluZbBfFleWzCyPXRoOytP0lE9pPp4r2UFOySfrqLk/Zlsheee");
        put("angela.martin", "$2b$12$Sq8uuwHMaqDzG776ixfKguxcKLex54QXOvHy5MGLM1XqYS2RYn1JW");
        put("oscar.martinez", "$2b$12$CifkinnNxJPiKLkmfgEuBe18bEMy4uq3f9HWrtN8sJli/JM5KTyu.");
        put("phyllis.vance", "$2b$12$xE4xBYFmEiFghX4tEx6st.fU5ENq8WabUt.HxCjSPvnKsB.ClE7QO");
        put("kelly.kapoor", "$2b$12$.Nb0ZMYMdRXscObiPPUDKOllb/9zRCMYzdjE0fwbItEEVaWMPfM3u");
        put("toby.flenderson", "$2b$12$/pQLchnod099waGEEpquau2uMjiurvr.TjFPnaeTQOVMDgj3EiQYG");
        put("creed.bratton", "$2b$12$tHLmLzpS8FC1ClOSkWDtIOGKFwFVcO0QW1yT3tw9/BrjYhnzrJwYe");
        put("meredith.palmer", "$2b$12$Jj/AFGZ0SHcAfQhy4BLzQuTFIFz8b99d59UMH13gMzIdM31dWxWom");
        put("ryan.howard", "$2b$12$fT0KoCncpB4shLEzCYonAuNExy9OJT5ONTUw/./jznRB1aDtsFlC2");
        put("darryl.philbin", "$2b$12$mu8koZ/uLu/sQNB6NRAau.Cemhi7dutDoRS62xYkpyCyNgMnunWC.");
        put("holly.flax", "$2b$12$FC6M/w8YmCUnU08QCVUAi.c65r6.fO6UlZzthpjveV4D8P66e8.Ku");
        put("jan.levinson", "$2b$12$E7BhQ4/4ToACH1DTUpDH.OlAkWBQ95iBNQrajorvnOqqHF4875gQq");
    }};

    @Test
    @DisplayName("PRE-FIX Proof: All leaked plaintext passwords from V31 match the V31 password hashes")
    void preFix_verifyLeakedPasswordsMatchV31Hashes() {
        assertThat(LEAKED_CREDENTIALS).hasSize(17);
        assertThat(V31_HASHES).hasSize(17);

        for (Map.Entry<String, String> entry : LEAKED_CREDENTIALS.entrySet()) {
            String username = entry.getKey();
            String plainPassword = entry.getValue();
            String originalHash = V31_HASHES.get(username);

            assertThat(originalHash)
                    .as("Hash for user %s must exist in V31 baseline", username)
                    .isNotNull();

            // Proves DEF-02 vulnerability in V31: leaked password matches stored hash
            assertThat(encoder.matches(plainPassword, originalHash))
                    .as("Leaked password for %s must match V31 hash (PRE-FIX vulnerability)", username)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("POST-FIX Proof: V37 rotates all 17 passwords with valid BCrypt hashes and invalidates leaked ones")
    void postFix_verifyV37RotationRemediatesAllLeakedPasswords() throws IOException {
        Path v37Path = Path.of("src/main/resources/db/migration/V37__rotate_seed_user_passwords.sql");
        assertThat(Files.exists(v37Path))
                .as("V37 migration file must exist")
                .isTrue();

        String sql = Files.readString(v37Path);

        // Pattern to extract WHEN 'username' THEN '$2...hash'
        Pattern pattern = Pattern.compile("WHEN\\s+'([a-zA-Z0-9.]+)'\\s+THEN\\s+'(\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53})'");
        Matcher matcher = pattern.matcher(sql);

        Map<String, String> v37RotatedHashes = new LinkedHashMap<>();
        while (matcher.find()) {
            v37RotatedHashes.put(matcher.group(1), matcher.group(2));
        }

        assertThat(v37RotatedHashes)
                .as("V37 must rotate all 17 seed accounts")
                .hasSameSizeAs(LEAKED_CREDENTIALS);

        for (Map.Entry<String, String> entry : LEAKED_CREDENTIALS.entrySet()) {
            String username = entry.getKey();
            String plainPassword = entry.getValue();
            String newHash = v37RotatedHashes.get(username);

            assertThat(newHash)
                    .as("Rotated hash for %s must be present in V37", username)
                    .isNotNull();

            // 1. Must be different from old V31 hash
            assertThat(newHash)
                    .as("New hash must not equal old leaked hash for %s", username)
                    .isNotEqualTo(V31_HASHES.get(username));

            // 2. Old leaked password must NOT match new hash!
            assertThat(encoder.matches(plainPassword, newHash))
                    .as("Leaked password for %s must FAIL to authenticate against V37 rotated hash", username)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("Invariance Proof: V31 migration file remains untouched (V1-V36 immutability rule)")
    void invariance_v31MigrationRemainsUntouched() throws IOException {
        Path v31Path = Path.of("src/main/resources/db/migration/V31__seed_warehouse_data_final.sql");
        assertThat(Files.exists(v31Path)).isTrue();
        String v31Content = Files.readString(v31Path);

        // V31 still contains original lines to preserve Flyway checksum across existing DB environments
        assertThat(v31Content).contains("-- michael.scott -> password: Office%13");
    }
}
