package com.logunovinc.workshopmanager.feature.tasks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.logunovinc.workshopmanager.domain.order.ProfileDrawing
import kotlin.math.*

@Composable
fun ProfileDrawingContent(drawing:ProfileDrawing) {
    val profiles=remember(drawing) {
      (if(drawing.skewed) listOf(false,true) else listOf(false)).map { end ->
        var angle=0.0;var x=0.0;var y=0.0
        buildList {
            add(x to y)
            drawing.segments.forEachIndexed { i,s ->
                if(i>0) angle+=(180-drawing.angles[i-1].toDouble())*(if(drawing.turns[i-1]=="left") 1 else -1)
                val length=if(end) requireNotNull(s.endMm) else s.startMm
                x+=length*cos(angle*PI/180);y-=length*sin(angle*PI/180);add(x to y)
            }
        }
      }
    }
    Text("Чертёж",style=MaterialTheme.typography.titleMedium)
    Canvas(Modifier.fillMaxWidth().height(220.dp).background(Color.White)) {
        val points=profiles.flatten()
        val minX=points.minOf { it.first };val minY=points.minOf { it.second }
        val span=max(max(points.maxOf { it.first }-minX,points.maxOf { it.second }-minY),1.0)
        val scale=(min(size.width,size.height)-32f)/span
        profiles.forEachIndexed { index,profile -> profile.zipWithNext().forEach { (a,b) ->
            drawLine(if(index==0) Color(0xff174a65) else Color(0xffb35a00),Offset((16+(a.first-minX)*scale).toFloat(),(16+(a.second-minY)*scale).toFloat()),Offset((16+(b.first-minX)*scale).toFloat(),(16+(b.second-minY)*scale).toFloat()),strokeWidth=3f)
        }
        }
    }
    Text("Длина ${drawing.lengthMm} мм · заготовка ${drawing.blankWidthMm} мм")
    if(drawing.skewed)Text("Косое изделие: синий — начало, оранжевый — конец. Размеры в начале / в конце")
    drawing.segments.forEachIndexed { i,s ->
        Text("Полка ${i+1}: ${s.startMm}${if(drawing.skewed) " / ${s.endMm}" else ""} мм")
        if(i<drawing.angles.size)Text("Сгиб ${i+1}: ${drawing.angles[i].stripTrailingZeros().toPlainString()}° · ${if(drawing.turns[i]=="left") "влево" else "вправо"}")
    }
    listOf(drawing.edge1,drawing.edge2).forEachIndexed { i,e ->
        val name=when(e.type){"hem"->"Подгиб 10 мм";"seam"->"Фальц 10 мм";else->"Без обработки"}
        Text("Край ${i+1}: $name${if(e.type!="none") if(e.direction=="in") ", внутрь" else ", наружу" else ""}")
    }
}
