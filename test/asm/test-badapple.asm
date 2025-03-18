
ccopy:
	PUSH BP
	MOVW BP, SP
	PUSH I
	PUSH J
	PUSH K
	PUSH L
	MOVW J:I, ptr [BP + 8]
	MOV AL, byte [J:I]
	MOVZ A, AL
	MOV K, A
	MOV L, 1

ccopy.loop0:
	MOV A, K
	CMP L, A
	JG ccopy.endloop0

ccopy.start0:
	MOVW J:I, ptr [BP + 12]
	MOV A, L
	DEC A
	MOVZ D:A, A
	ADD I, A
	ADC J, D
	MOVW B:C, J:I
	MOVW J:I, ptr [BP + 8]
	MOV A, L
	MOVZ D:A, A
	ADD I, A
	ADC J, D
	MOV AL, byte [J:I]
	MOVW J:I, B:C
	MOV byte [J:I], AL

ccopy.next0:
	INC L
	JMP ccopy.loop0

ccopy%epilogue:
ccopy.endloop0:
	POP L
	POP K
	POP J
	POP I
	POP BP
	RET

frame:
	PUSH BP
	MOVW BP, SP
	SUB SP byte 11
	PUSH I
	PUSH J
	PUSH K
	PUSH L
	MOVW L:K, ptr [BP + 8]
	MOVW B:C, -268304384
	MOVW D:A, B:C
	ADD A, word 11264
	ADC D, word 1
	MOVW J:I, D:A

frame.loop0:
frame.next0:
	MOVW D:A, J:I
	CMP B, D
	JG frame.cmpd0
	JL frame.cmpd0
	CMP C, A

frame.cmpd0:
	CMOVGE AL, 1
	CMOVL AL, 0
	CMP 0 AL
	JNZ frame.endloop0

frame.start0:
	MOV AL, byte [L:K]
	MOV byte [BP + -1], AL
	AND AL, byte 252
	MOV byte [BP + -2], AL
	MOV AL, byte [BP + -1]
	AND AL, byte 3
	MOV byte [BP + -3], AL
	CMP byte [BP + -3], byte 0
	JNZ frame.elseif0_0
	INC K
	ICC L
	MOV AL, byte [L:K]
	MOV byte [BP + -4], AL
	MOVZ A, AL
	SHL A, word 8
	PUSH A
	MOV AL, byte [BP + -2]
	MOVZ A, AL
	OR A, word [SP]
	ADD SP 2
	MOVZ D:A, A
	ADD C, A
	ADC B, D
	JMP frame.endif0

frame.elseif0_0:
	CMP byte [BP + -3], byte 2
	JNZ frame.elseif0_1
	MOV A, 0
	MOV word [BP + -5], A

frame.loop1:
	MOV AL, byte [BP + -2]
	MOVZ A, AL
	CMP word [BP + -5], A
	JGE frame.endloop1

frame.start1:
	MOVW ptr [BP + -9], J:I
	MOVW J:I, B:C
	MOVW D:A, 0
	MOVW ptr [J:I], D:A
	MOVW J:I, ptr [BP + -9]
	ADD C, word 4
	ADC B, word 0

frame.next1:
	MOV A, word [BP + -5]
	ADD A, word 4
	MOV word [BP + -5], A
	JMP frame.loop1
	JMP frame.endif0

frame.elseif0_1:
	CMP byte [BP + -3], byte 3
	JNZ frame.elseif0_2
	MOV A, 0
	MOV word [BP + -5], A

frame.loop2:
	MOV AL, byte [BP + -2]
	MOVZ A, AL
	CMP word [BP + -5], A
	JGE frame.endloop2

frame.start2:
	MOVW ptr [BP + -9], J:I
	MOVW J:I, B:C
	MOVW D:A, -1
	MOVW ptr [J:I], D:A
	MOVW J:I, ptr [BP + -9]
	ADD C, word 4
	ADC B, word 0

frame.next2:
	MOV A, word [BP + -5]
	ADD A, word 4
	MOV word [BP + -5], A
	JMP frame.loop2
	JMP frame.endif0

frame.elseif0_2:
	CMP byte [BP + -2], byte 252
	JNZ frame.else0
	INC K
	ICC L
	PUSH B
	PUSH C
	PUSH B
	PUSH C
	PUSH L
	PUSH K
	CALL ccopy
	ADD SP byte 8
	POP C
	POP B
	MOV AL, byte [L:K]
	MOV byte [BP + -4], AL
	MOVZ A, AL
	MOVZ D:A, A
	ADD C, A
	ADC B, D
	MOV AL, byte [BP + -4]
	MOVZ A, AL
	MOVZ D:A, A
	ADD K, A
	ADC L, D
	JMP frame.endif0

frame.else0:
	MOV AL, byte [BP + -2]
	MOVZ A, AL
	MOVZ D:A, A
	ADD A, ptr [BP + 12]
	ADC D, ptr [BP + 14]
	MOVW ptr [BP + -7], D:A
	MOVW D:A, ptr [D:A]
	MOVW ptr [BP + -11], D:A
	PUSH B
	PUSH C
	PUSH B
	PUSH C
	MOVW D:A, ptr [BP + -11]
	PUSH D
	PUSH A
	CALL ccopy
	ADD SP byte 8
	POP C
	POP B
	MOVW D:A, ptr [BP + -11]
	MOV AL, byte [D:A]
	MOVZ A, AL
	MOVZ D:A, A
	ADD C, A
	ADC B, D

frame.endloop1:
frame.endloop2:
frame.endif0:
	INC K
	ICC L
	JMP frame.loop0

frame.endloop0:
	MOVW D:A, L:K

frame%epilogue:
	POP L
	POP K
	POP J
	POP I
	ADD SP byte 11
	POP BP
	RET

main:
	PUSH BP
	MOVW BP, SP
	SUB SP byte 6
	PUSH I
	PUSH J
	PUSH K
	PUSH L
	MOVW L:K, 48
	MOVW J:I, L:K
	LEA D:A, ptr [x]
	MOVW ptr [J:I], D:A
	MOV B, 0

main.loop0:
	CMP B, 255
	JNC main.endloop0

main.start0:
	MOV A, B
	SHL A, word 8
	OR A, B
	MOVZ D:A, A
	PUSH D
	PUSH A
	MOV A, B
	MOVZ D:A, A
	MOV D, A
	MOV A, 0
	OR A, ptr [SP]
	OR D, ptr [SP + 2]
	ADD SP 4
	MOVW J:I, D:A
	PUSH B
	PUSH J
	PUSH I
	MOV A, B
	PUSH AL
	CALLA ptr gutil.set_color
	ADD SP byte 5
	POP B

main.next0:
	INC B
	JMP main.loop0

main.endloop0:
	MOVW B:C, ptr badapple.colorWhite
	MOVW J:I, ptr badapple.colorBlack
	PUSH B
	PUSH C
	PUSHW ptr 0
	PUSH byte 0
	CALLA ptr gutil.set_color
	ADD SP byte 5
	POP C
	POP B
	PUSH B
	PUSH C
	PUSHW ptr 0
	MOVW D:A, J:I
	PUSH byte [D:A]
	CALLA ptr gutil.set_color
	ADD SP byte 5
	POP C
	POP B
	PUSH B
	PUSH C
	PUSHW ptr -1
	PUSH byte 255
	CALLA ptr gutil.set_color
	ADD SP byte 5
	POP C
	POP B
	PUSH B
	PUSH C
	PUSHW ptr -1
	MOVW D:A, B:C
	PUSH byte [D:A]
	CALLA ptr gutil.set_color
	ADD SP byte 5
	POP C
	POP B
	MOVW D:A, ptr badapple.f0
	MOVW ptr [BP + -4], D:A
	MOV A, 0
	MOV word [BP + -6], A

main.loop1:
	MOV A, word [BP + -6]
	CMP A, 6571
	JNC main.endloop1

main.start1:
	PUSH B
	PUSH C
	PUSHW ptr badapple.customRegionTable
	MOVW D:A, ptr [BP + -4]
	PUSH D
	PUSH A
	CALL frame
	ADD SP byte 8
	POP C
	POP B
	MOVW ptr [BP + -4], D:A
	PUSH B
	PUSH C
	CALLA ptr util.halt
	POP C
	POP B

main.next1:
	INC word [BP + -6]
	JMP main.loop1

main.next2:
main.start2:
main.endloop1:
main.loop2:
	PUSH B
	PUSH C
	CALLA ptr util.halt
	POP C
	POP B
	JMP main.loop2

main.endloop2:
main%epilogue:
	POP L
	POP K
	POP J
	POP I
	ADD SP byte 6
	POP BP
	RET

x:
	resb -31, 0, 0, 0
