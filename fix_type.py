with open("app/src/main/java/com/example/ui/theme/Type.kt", "r") as f:
    text = f.read()

text = text.replace("letterSpacing = 0.5.sp,\n      )\n        titleLarge", "letterSpacing = 0.5.sp,\n      ),\n        titleLarge")
with open("app/src/main/java/com/example/ui/theme/Type.kt", "w") as f:
    f.write(text)
