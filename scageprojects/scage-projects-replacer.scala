#!/bin/sh
exec scala -J-Xmx4G -savecompiled -deprecation "$0" "$@"
!#

// какие файлы надо вынести в общие?

// запускаемся из папки dunnololda.github.io/scageprojects

import java.util._
import java.io._
import sys.process._

// названия проектов
val scageprojects = new File(".").listFiles.filter(_.isDirectory).map(_.getName)

var jars_in_common = new File("../common").listFiles.map(_.getName).filter(_.endsWith(".jar")).toSet
def updateJarsInCommon() {
  jars_in_common = new File("../common").listFiles.map(_.getName).filter(_.endsWith(".jar")).toSet
}

// обновляем все run.jnlp во всех проектах, чтобы в качестве джарников использовали джарники из common
scageprojects.foreach {
  case name =>
    println(s"processing: $name")
    val fos = new FileOutputStream(s"$name/run.jnlp.tmp")
    var any_difference = false
    for {
      line <- io.Source.fromFile(s"$name/run.jnlp").getLines
    } {
      if(line.contains("<jar href=") && !line.contains("../../common/") && !line.contains("main=\"true\"")) {
        jars_in_common.find(x => line.contains(x)) match {
          case Some(x) =>
            val updated_line = line.replace(x, s"../../common/$x")
            fos.write(s"$updated_line\n".getBytes)
            println(s"[$name/run.jnlp] ${line.trim} -> ${updated_line.trim}")
          case None =>
            val jarfile = line.replace("<jar href=", "").replace("/>", "").replace("\"", "").trim
            val command = s"mv $name/$jarfile ../common/"
            println(command)
            command.!
            updateJarsInCommon()
            val updated_line = line.replace(jarfile, s"../../common/$jarfile")
            fos.write(s"$updated_line\n".getBytes)
            println(s"[$name/run.jnlp] ${line.trim} -> ${updated_line.trim}")            
        }
        any_difference = true
      } else if(line.contains("""<nativelib href="natives/lwjgl-natives.jar"/>""")) {
        val updated_line = line.replace("""<nativelib href="natives/lwjgl-natives.jar"/>""", """<nativelib href="../../common/natives/lwjgl-natives.jar"/>""")
        fos.write(s"$updated_line\n".getBytes)
        println(s"[$name/run.jnlp] $line -> ${line.trim} -> ${updated_line.trim}")
        any_difference = true
      } else {
        fos.write(s"$line\n".getBytes)
      }
    }
    fos.close()
    if(any_difference) {
      val command = s"mv $name/run.jnlp.tmp $name/run.jnlp"
      println(command)
      command.!
    } else {
      new File(s"$name/run.jnlp.tmp").delete()
    }
}

// удаляем все джарники в проектах, которые есть в common
scageprojects.foreach {
  case name =>
    val files = new File(s"$name").listFiles
    files.foreach {
      case f => 
        if(f.getName == "natives" && f.isDirectory) {
          println(s"deleting ${f.getAbsolutePath}")
          f.listFiles.foreach(ff => ff.delete())
          f.delete()
        } else if(jars_in_common.contains(f.getName)) {
          println(s"deleting ${f.getAbsolutePath}")
          f.delete()
        }
    }
}

// ищем джарники в common, которые не используются больше нигде и выводим их список
val jars_in_common_not_used_anywhere = jars_in_common.toBuffer
scageprojects.foreach {
  case name =>
  for {
      line <- io.Source.fromFile(s"$name/run.jnlp").getLines
    } {
      if(line.contains("<jar href=") && line.contains("../../common/") && !line.contains("main=\"true\"")) {
        jars_in_common_not_used_anywhere.find(x => line.contains(x)).foreach(x => jars_in_common_not_used_anywhere -= x)
      }
    }
}
if(jars_in_common_not_used_anywhere.nonEmpty) {
  println("jars in common which are not used:")
  jars_in_common_not_used_anywhere.foreach(println)
}
