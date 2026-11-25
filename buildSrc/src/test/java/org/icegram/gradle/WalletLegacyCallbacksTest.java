package org.icegram.gradle;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/** Standalone bytecode tests; not an Android/Google Pay runtime test. */
public final class WalletLegacyCallbacksTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        byte[] jar = entry(Files.readAllBytes(Paths.get(args[0])), "classes.jar");
        byte[] original = entry(jar, WalletLegacyCallbacks.CLASS_NAME.replace('.', '/') + ".class");
        byte[] patched = transform(original, 0);
        ClassNode before = parse(original);
        ClassNode after = parse(patched);
        check(before.methods.size() == after.methods.size(), "Only dispatcher instructions change");
        check(before.superName.equals(after.superName), "Superclass preserved");
        check(before.interfaces.equals(after.interfaces), "Interfaces preserved");

        MethodNode oldDispatch = dispatcher(before);
        MethodNode newDispatch = dispatcher(after);
        LookupSwitchInsnNode oldSwitch = dispatch(oldDispatch);
        LookupSwitchInsnNode newSwitch = dispatch(newDispatch);
        check(newSwitch.keys.equals(Arrays.asList(
                3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)),
                "Only obsolete transaction codes 1 and 2 removed");
        for (int key : newSwitch.keys) {
            check(branch(oldDispatch, oldSwitch, key).equals(branch(newDispatch, newSwitch, key)),
                    "Transaction " + key + " instructions unchanged");
        }
        check(branch(oldDispatch, oldSwitch, null).equals(branch(newDispatch, newSwitch, null)),
                "Unknown transaction handling unchanged");
        check(branch(newDispatch, newSwitch, null).contains("ICONST_0"),
                "Unsupported legacy transactions return false");
        check(branch(newDispatch, newSwitch, 9).contains(".zzc ("), "isReadyToPay callback retained");
        check(branch(newDispatch, newSwitch, 14).contains(".zzf ("), "PaymentData callback retained");
        for (MethodNode method : after.methods) {
            new Analyzer<>(new BasicVerifier()).analyze(after.name, method);
            check(true, "Valid JVM operand stacks: " + method.name);
            if (!method.name.equals("zza")) {
                MethodNode old = before.methods.stream().filter(m -> m.name.equals(method.name)
                        && m.desc.equals(method.desc)).findFirst().orElseThrow();
                check(trace(old).equals(trace(method)), "Non-dispatch method unchanged");
            }
        }
        String text = trace(newDispatch);
        check(!text.contains("MaskedWallet") && !text.contains("FullWallet"),
                "No legacy deserialization/callback references in dispatcher");
        check(text.contains("PaymentData.CREATOR"), "Modern payment deserialization retained");

        check(trace(newDispatch).equals(trace(dispatcher(parse(
                transform(original, ClassReader.EXPAND_FRAMES))))),
                "AGP expanded-frame input supported");
        transform(original, ClassReader.SKIP_FRAMES);
        check(true, "AGP frame-free input supported");
        reject(patched, "Already modified/unknown SDK bytecode rejected");

        ClassNode changed = parse(original);
        dispatcher(changed).instructions.insert(new InsnNode(Opcodes.NOP));
        reject(write(changed), "Even an unexpected NOP rejected");
        changed = parse(original);
        dispatch(dispatcher(changed)).keys.set(0, 0);
        reject(write(changed), "Changed Binder transaction mapping rejected");
        changed = parse(original);
        dispatcher(changed).name = "unexpected";
        reject(write(changed), "Missing expected dispatcher rejected");
        System.out.println("Wallet callback bytecode tests: " + checks + " checks passed");
        System.out.println("All 17 retained transactions unchanged; Android payment runtime not tested.");
    }

    private static byte[] entry(byte[] zip, String name) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().equals(name)) return in.readAllBytes();
            }
        }
        throw new AssertionError("Missing official fixture entry: " + name);
    }

    private static byte[] transform(byte[] data, int flags) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(data).accept(new WalletLegacyCallbacks(writer), flags);
        return writer.toByteArray();
    }

    private static ClassNode parse(byte[] data) {
        ClassNode node = new ClassNode();
        new ClassReader(data).accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode dispatcher(ClassNode node) {
        return node.methods.stream().filter(m -> m.name.equals("zza")).findFirst().orElseThrow();
    }

    private static LookupSwitchInsnNode dispatch(MethodNode method) {
        for (AbstractInsnNode n : method.instructions) {
            if (n instanceof LookupSwitchInsnNode) return (LookupSwitchInsnNode) n;
        }
        throw new AssertionError("No dispatcher switch");
    }

    private static String branch(MethodNode method, LookupSwitchInsnNode dispatch, Integer key) {
        LabelNode start = key == null ? dispatch.dflt : dispatch.labels.get(dispatch.keys.indexOf(key));
        Set<LabelNode> boundaries = new HashSet<>(dispatch.labels);
        boundaries.add(dispatch.dflt);
        Textifier text = new Textifier();
        TraceMethodVisitor visitor = new TraceMethodVisitor(text);
        AbstractInsnNode node = start;
        do {
            if (!(node instanceof FrameNode) && !(node instanceof LineNumberNode)) node.accept(visitor);
            node = node.getNext();
        } while (node != null && !boundaries.contains(node));
        return printed(text);
    }

    private static String trace(MethodNode method) {
        Textifier text = new Textifier();
        method.accept(new TraceMethodVisitor(text));
        return printed(text);
    }

    private static String printed(Textifier text) {
        StringWriter string = new StringWriter();
        text.print(new PrintWriter(string));
        return string.toString();
    }

    private static void reject(byte[] input, String message) {
        try {
            transform(input, 0);
        } catch (IllegalStateException expected) {
            check(true, message);
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
