import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Bf_compiler {

    private static int labelCounter;

    public static void main(String[] args) throws Exception {

        if (args.length != 1 || !args[0].endsWith(".bf")) {
            System.err.println("Usage: ./bf_compiler <file>.bf");
            System.exit(1);
        }

        Path inputPath = Paths.get(args[0]).toAbsolutePath();

        if (!Files.exists(inputPath)) {
            error("File not found.");
        }

        labelCounter = 0;

        String baseName = inputPath.getFileName().toString().replace(".bf", "");
        Path parentDir = inputPath.getParent();

        Path asmPath = parentDir.resolve(baseName + ".s");
        Path objPath = parentDir.resolve(baseName + ".o");
        Path exePath = parentDir.resolve(baseName);

        String source = Files.readString(inputPath);

        List<Character> tokens = scan(source);
        parse(tokens);
        generateAssembly(tokens, asmPath);
        assembleAndLink(asmPath, objPath, exePath);

        System.out.println("Compilation successful: " + exePath);
    }

    // ============================
    // STAGE 1 — SCANNER
    // ============================
    private static List<Character> scan(String source) {

        List<Character> tokens = new ArrayList<>();
        String valid = "><+-.,[]";

        for (char c : source.toCharArray()) {

            if (valid.indexOf(c) != -1) {
                tokens.add(c);

            } else if (!Character.isWhitespace(c)) {
                error("Invalid character detected: " + c);
            }
        }

        return tokens;
    }

    // ============================
    // STAGE 2 — PARSER
    // ============================
    private static void parse(List<Character> tokens) {

        Stack<Integer> stack = new Stack<>();

        for (int i = 0; i < tokens.size(); i++) {

            char c = tokens.get(i);

            if (c == '[') {
                stack.push(i);

            } else if (c == ']') {

                if (stack.isEmpty()) {
                    error("Unmatched ] at position " + i);
                }

                stack.pop();
            }
        }

        if (!stack.isEmpty()) {
            error("Missing ] for [ at position " + stack.peek());
        }
    }

    // ============================
    // STAGE 3 — CODE GENERATION
    // ============================
    private static void generateAssembly(List<Character> tokens, Path output) throws IOException {

        try (BufferedWriter writer = Files.newBufferedWriter(output)) {

            writer.write(".section .bss\n");
            writer.write("memory: .skip 30000\n\n");

            writer.write(".section .text\n");
            writer.write(".global _start\n");
            writer.write("_start:\n");

            writer.write("    lea memory(%rip), %r12\n");

            Stack<Integer> loopStack = new Stack<>();

            for (char c : tokens) {

                switch (c) {

                    case '>':
                        writer.write("    inc %r12\n");
                        break;

                    case '<':
                        writer.write("    dec %r12\n");
                        break;

                    case '+':
                        writer.write("    incb (%r12)\n");
                        break;

                    case '-':
                        writer.write("    decb (%r12)\n");
                        break;

                    case '.':
                        writer.write(
                                "    mov $1, %rax\n" +
                                "    mov $1, %rdi\n" +
                                "    mov %r12, %rsi\n" +
                                "    mov $1, %rdx\n" +
                                "    syscall\n"
                        );
                        break;

                    case ',':
                        writer.write(
                                "    mov $0, %rax\n" +
                                "    mov $0, %rdi\n" +
                                "    mov %r12, %rsi\n" +
                                "    mov $1, %rdx\n" +
                                "    syscall\n"
                        );
                        break;

                    case '[':
                        int startLabel = labelCounter++;
                        loopStack.push(startLabel);

                        writer.write("loop_start_" + startLabel + ":\n");
                        writer.write("    cmpb $0, (%r12)\n");
                        writer.write("    je loop_end_" + startLabel + "\n");
                        break;

                    case ']':
                        int label = loopStack.pop();
                        writer.write("    jmp loop_start_" + label + "\n");
                        writer.write("loop_end_" + label + ":\n");
                        break;
                }
            }

            writer.write(
                    "    mov $60, %rax\n" +
                    "    mov $0, %rdi\n" +
                    "    syscall\n"
            );
        }
    }

    // ============================
    // STAGE 4 — ASSEMBLE + LINK
    // ============================
    private static void assembleAndLink(Path asm, Path obj, Path exe) throws Exception {

        ProcessBuilder as = new ProcessBuilder(
                "as",
                asm.toString(),
                "-o",
                obj.toString()
        );

        as.inheritIO();
        Process assemble = as.start();

        if (assemble.waitFor() != 0) {
            error("Assembly failed.");
        }

        ProcessBuilder ld = new ProcessBuilder(
                "ld",
                obj.toString(),
                "-o",
                exe.toString()
        );

        ld.inheritIO();
        Process link = ld.start();

        if (link.waitFor() != 0) {
            error("Linking failed.");
        }
    }

    private static void error(String message) {
        System.err.println("Compilation error: " + message);
        System.exit(1);
    }
}
