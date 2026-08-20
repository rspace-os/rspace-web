package com.researchspace.core.util;


import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscapeReplacementTest {

    @Test
    void testEscapeChars() {
        String test = "A<B>C&D\"E F\tG!H#I$J%K\'L(M)N*O+P,Q/R=S?T@U[V]W^X|Y{Z}A~B";
        String rslt = "A_B_C_D_E_F_G_H_I_J_K_L_M_N_O_P_Q_R_S_T_U_V_W_X_Y_Z_A_B";
        String rs = EscapeReplacement.replaceChars(test);
        assertTrue(rs.equals(rslt));
    }

    String toHex(String input) {
        return new String(Hex.encodeHex(input.getBytes(StandardCharsets.UTF_8))).toUpperCase();
    }

    String fromHex(String input) throws DecoderException {
        byte[] bytes = Hex.decodeHex(input.toCharArray());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    void escapeNBSPWhitespaceChars() throws DecoderException {
        // this is just a sanity check that encoding/decoding is working
        String hexString = "6EC2A05254";
        String fromHex = fromHex(hexString);
        // this is a string with whitespace encoded by NBSP chars, that is not considered whitespace by Character.isWhitespace()
        assertEquals("n RT", fromHex);
        assertEquals(hexString, toHex(fromHex));

        String unicodeWithNBSPs = "A\u00A0B\u2007C\u202FD";

        // note C2A0 = UTF8hex for \u00A0; E28087 is UTF8hex for \u2007; E280AF is UTF8hex for \u202F
        assertEquals("41C2A042E2808743E280AF44", toHex(unicodeWithNBSPs));

        String escaped = EscapeReplacement.replaceChars(unicodeWithNBSPs);
        assertEquals("A_B_C_D", escaped);
        assertEquals(7, escaped.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void generalWhitespaceEscape(){
        String toEscape = "A\u001CB";
        String escaped = EscapeReplacement.replaceChars(toEscape);
        assertEquals("A_B", escaped);

    }

}
