package org.icegram.gradle;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Build-time-only removal of obsolete pre-PaymentData Wallet callbacks.
 * PaymentFormActivity uses PaymentsClient, not MaskedWallet/FullWallet. The old
 * callbacks deserialize pass objects referencing Maps even without an app caller.
 * Keep every other transaction, including isReadyToPay (9) and PaymentData (14).
 */
public final class WalletLegacyCallbacks extends ClassVisitor {
    public static final String CLASS_NAME = "com.google.android.gms.internal.wallet.zzp";
    private static final String DESCRIPTOR = "(ILandroid/os/Parcel;Landroid/os/Parcel;I)Z";
    // Canonical dispatcher from the official play-services-wallet:19.2.1 artifact.
    // Fail closed on SDK/bytecode changes instead of guessing obfuscated names.
    private static final String EXPECTED_HASH =
            "9ff2bdf7d1ac611f2ce0572fdf7ea0eb0954786910ca7345f8db985a66bfaf79";
    private boolean found;

    public WalletLegacyCallbacks(ClassVisitor next) {
        super(Opcodes.ASM9, next);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
            String signature, String[] exceptions) {
        MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (!name.equals("zza") || !descriptor.equals(DESCRIPTOR)) {
            return next;
        }
        if (found) {
            throw new IllegalStateException("Duplicate Wallet callback dispatcher");
        }
        found = true;
        return new MethodNode(Opcodes.ASM9, access, name, descriptor, signature, exceptions) {
            @Override
            public void visitEnd() {
                String hash = fingerprint(this);
                if (!EXPECTED_HASH.equals(hash)) {
                    throw new IllegalStateException("Unrecognized Wallet 19.2.1 dispatcher: " + hash);
                }
                removeLegacyCases(this);
                accept(next);
            }
        };
    }

    @Override
    public void visitEnd() {
        if (!found) {
            throw new IllegalStateException("Wallet callback dispatcher not found");
        }
        super.visitEnd();
    }

    private static String fingerprint(MethodNode method) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "WalletDispatcherFingerprint",
                null, "java/lang/Object", null);
        MethodVisitor target = writer.visitMethod(method.access, method.name, method.desc,
                method.signature, method.exceptions.toArray(new String[0]));
        // AGP may recalculate frames. They and debug metadata are not executable code.
        method.accept(new MethodVisitor(Opcodes.ASM9, target) {
            @Override public void visitFrame(int type, int nl, Object[] local, int ns, Object[] stack) {}
            @Override public void visitLineNumber(int line, Label start) {}
            @Override public void visitLocalVariable(String n, String d, String s,
                    Label start, Label end, int index) {}
        });
        writer.visitEnd();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(writer.toByteArray());
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format("%02x", b & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void removeLegacyCases(MethodNode method) {
        LookupSwitchInsnNode dispatch = null;
        for (AbstractInsnNode node : method.instructions) {
            if (node instanceof LookupSwitchInsnNode) {
                if (dispatch != null) throw new IllegalStateException("Multiple Wallet switches");
                dispatch = (LookupSwitchInsnNode) node;
            }
        }
        if (dispatch == null || !dispatch.keys.equals(Arrays.asList(
                1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20))) {
            throw new IllegalStateException("Unexpected Wallet transaction codes");
        }
        Set<LabelNode> boundaries = new HashSet<>(dispatch.labels);
        boundaries.add(dispatch.dflt);
        ArrayList<AbstractInsnNode> removed = new ArrayList<>();
        for (int code : new int[] {1, 2}) {
            LabelNode start = dispatch.labels.get(dispatch.keys.indexOf(code));
            AbstractInsnNode node = start;
            do {
                removed.add(node);
                node = node.getNext();
            } while (node != null && !boundaries.contains(node));
            if (node == null) throw new IllegalStateException("Unbounded legacy Wallet case");
        }
        // Unsupported legacy transaction codes now take the unchanged default (false).
        dispatch.keys.subList(0, 2).clear();
        dispatch.labels.subList(0, 2).clear();
        for (AbstractInsnNode node : removed) method.instructions.remove(node);
    }
}
