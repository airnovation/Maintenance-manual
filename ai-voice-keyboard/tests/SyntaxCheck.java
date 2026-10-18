import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;
public class SyntaxCheck {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<Path> paths;
            try (var walk = Files.walk(Path.of("app/src/main/java"))) {
                paths = walk.filter(p -> p.toString().endsWith(".java")).toList();
            }
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-source", "17", "-proc:none"), null, files.getJavaFileObjectsFromPaths(paths));
            task.parse();
            long errors = diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).count();
            for (var d : diagnostics.getDiagnostics()) System.out.println(d);
            if (errors != 0) throw new AssertionError("Java syntax errors: " + errors);
            System.out.println("PASS syntax parse of " + paths.size() + " Java sources (not Android API type checking)");
        }
    }
}
