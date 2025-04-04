
main:
	PUSH BP
	MOVW BP, SP
	SUB SP byte 2
	PUSH I
	PUSHW L:K
	MOVW L:K, x
	MOVW [48], L:K
	MOV B, word 0
	JMP .forcond%2
	MOV L, word 255
	CMP B, L
	JC .fortrue%3
	JMP .fordone%5
	PUSHW ptr 0
	PUSH byte 0
	CALL gutil.set_color
	ADD SP 5
	MOV CL, [badappledata.colorBlack]
	PUSHW ptr 0
	PUSH CL
	CALL gutil.set_color
	ADD SP 5
	PUSHW ptr -1
	PUSH byte -1
	CALL gutil.set_color
	ADD SP 5
	MOV CL, [badappledata.colorWhite]
	PUSHW ptr -1
	PUSH CL
	CALL gutil.set_color
	ADD SP 5
	MOV I, word 0
	MOVW L:K, badappledata.f0
	JMP .forcond%8

.forcond%8:
	MOV B, word 6571
	CMP I, B
	JC .fortrue%9
	JMP .whiletrue%14

.whiletrue%14:
	CALL util.halt
	JMP .whiletrue%14

.fortrue%9:
	CALL util.buffer_screen
	PUSHW badappledata.customRegionTable
	PUSHW L:K
	CALL frame
	ADD SP 8
	MOVW L:K, D:A
	CALL util.unbuffer_screen
	CALL util.halt
	ADD I, word 1
	LEA L:K, [L:K + 1]
	JMP .forcond%8
	MOV word [BP + -2], B
	MOV L, B
	SHL L, word 8
	MOVZ B:C, word [BP + -2]
	OR L, word [BP + -2]
	MOV B, C
	MOV C, 0
	MOVZ L:K, L
	OR C, K
	OR B, L
	PUSHW B:C
	MOV CL, byte [BP + -2]
	PUSH CL
	CALL gutil.set_color
	ADD SP 5
	MOV B, word [BP + -2]
	ADD B, word 1
	JMP .forcond%2

frame:
	PUSH BP
	MOVW BP, SP
	SUB SP byte 9
	PUSHW J:I
	PUSHW L:K
	MOVW B:C, ptr [BP + 8]
	MOVW L:K, ptr -268304384
	MOVW J:I, B:C
	JMP .whilecond%1

.whilecond%1:
	MOVW B:C, ptr -268227584
	CMP L, B
	JC .whiletrue%2
	JA .whiledone%3
	CMP K, C
	JC .whiletrue%2
	JMP .whiledone%3

.whiledone%3:
	MOVW D:A, J:I
	POPW L:K
	POPW J:I
	ADD SP byte 9
	POP BP
	RET

.whiletrue%2:
	MOVW B:C, J:I
	MOV CL, [B:C]
	MOV DL, byte 0
	MOV BL, CL
	AND BL, byte -4
	AND CL, byte 3
	CMP CL, DL
	JZ .iftrue%4
	JMP .elseif%5

.elseif%5:
	MOV DL, byte 2
	CMP CL, DL
	JZ .elseif%5%true%141
	JMP .elseif%7

.elseif%7:
	MOV DL, byte 3
	CMP CL, DL
	JZ .elseif%7%true%143
	JMP .elseif%9

.elseif%9:
	MOV DL, byte -4
	MOV CL, BL
	CMP CL, DL
	JZ .elseiftrue%10
	JMP .else%11

.else%11:
	MOV CL, BL
	MOVW D:A, ptr [BP + 12]
	MOVZ C, CL
	MOVZ B:C, C
	ADD A, C
	ADC D, B
	MOVW B:C, [D:A]
	MOVW ptr [BP + -9], B:C
	MOVW ptr [BP + -4], L:K
	PUSHW L:K
	MOVW L:K, ptr [BP + -9]
	PUSHW L:K
	CALL ccopy
	ADD SP 8
	MOVW L:K, ptr [BP + -9]
	MOVZ K, [L:K]
	MOVZ L:K, K
	MOVW D:A, J:I
	MOVW B:C, ptr [BP + -4]
	ADD C, K
	ADC B, L
	MOVW L:K, D:A
	MOVW D:A, B:C
	JMP .endif%12

.endif%12:
	LEA L:K, [L:K + 1]
	MOVW J:I, L:K
	MOVW L:K, D:A
	JMP .whilecond%1

.elseiftrue%10:
	MOVW ptr [BP + -4], L:K
	MOVW L:K, J:I
	MOVW B:C, ptr [BP + -4]
	PUSHW B:C
	LEA J:I, [L:K + 1]
	PUSHW J:I
	CALL ccopy
	ADD SP 8
	MOVZ K, [L:K + 1]
	MOVZ L:K, K
	MOVW B:C, J:I
	ADD C, K
	ADC B, L
	MOVW D:A, ptr [BP + -4]
	ADD A, K
	ADC D, L
	MOVW L:K, B:C
	JMP .endif%12

.elseif%7%true%143:
	MOV A, word 0
	JMP .forcond%15

.forcond%15:
	MOV CL, BL
	MOVZ C, CL
	CMP A, C
	JC .fortrue%16
	JMP .forcond%15%false%144

.forcond%15%false%144:
	MOVW D:A, L:K
	MOVW L:K, J:I
	JMP .endif%12

.fortrue%16:
	MOVW C:D, ptr -1
	MOVW [L:K], C:D
	ADD A, word 4
	LEA L:K, [L:K + 4]
	JMP .forcond%15

.elseif%5%true%141:
	MOVW ptr [BP + -4], L:K
	MOV A, word 0
	MOVW L:K, ptr [BP + -4]
	JMP .forcond%21

.forcond%21:
	MOV CL, BL
	MOVZ C, CL
	CMP A, C
	JC .fortrue%22
	JMP .forcond%21%false%142

.forcond%21%false%142:
	MOVW D:A, L:K
	MOVW L:K, J:I
	JMP .endif%12

.fortrue%22:
	MOVW C:D, ptr 0
	MOVW [L:K], C:D
	ADD A, word 4
	LEA L:K, [L:K + 4]
	JMP .forcond%21

.iftrue%4:
	MOVW D:A, J:I
	MOV CL, BL
	MOVZ B, [D:A + 1]
	MOVZ I, CL
	SHL B, word 8
	OR I, B
	LEA B:C, [D:A + 1]
	LEA D:A, [L:K + I]
	MOVW L:K, B:C
	JMP .endif%12

ccopy:
	PUSH BP
	MOVW BP, SP
	PUSH I
	PUSH L

.entry:
	MOVW D:A, ptr [BP + 8]
	MOVZ I, [D:A]
	MOV B, word 1
	JMP .forcond%2

.forcond%2:
	CMP B, I
	JBE .fortrue%3
	JMP .fordone%5

.fordone%5:
	POP L
	POP I
	POP BP
	RET

.fortrue%3:
	MOVW D:A, ptr [BP + 8]
	MOV CL, [D:A + B]
	MOVW D:A, ptr [BP + 12]
	MOV L, B
	SUB L, word 1
	MOV [D:A + L], CL
	ADD B, word 1
	JMP .forcond%2

x:
	dp 225
