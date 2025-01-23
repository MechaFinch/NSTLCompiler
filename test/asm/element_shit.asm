
main:
	PUSH BP
	MOVW BP, SP

main%epilogue:
	POP BP
	RET

mat_add:
	PUSH BP
	MOVW BP, SP
	SUB SP byte 12
	PUSH I
	PUSH J
	PUSH K
	PUSH L
	MOVW J:I, ptr [BP + 16]
	PUSH word [J:I + 2]
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I + 2]
	CMP A, word [SP]
	CMOVNZ A, 1
	CMOVZ A, 0
	ADD SP 2
	PUSH A
	MOVW J:I, ptr [BP + 16]
	PUSH word [J:I]
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I]
	CMP A, word [SP]
	CMOVNZ A, 1
	CMOVZ A, 0
	ADD SP 2
	PUSH A
	MOVW J:I, ptr [BP + 12]
	PUSH word [J:I + 2]
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I + 2]
	CMP A, word [SP]
	CMOVNZ A, 1
	CMOVZ A, 0
	ADD SP 2
	PUSH A
	MOVW J:I, ptr [BP + 12]
	PUSH word [J:I]
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I]
	CMP A, word [SP]
	CMOVNZ A, 1
	CMOVZ A, 0
	ADD SP 2
	OR A, word [SP]
	ADD SP 2
	OR A, word [SP]
	ADD SP 2
	OR A, word [SP]
	ADD SP 2
	CMP 0 A
	JZ mat_add.endif0
	MOV A 1
	JMP mat_add%epilogue

mat_add.endif0:
	MOV K, 0

mat_add.loop0:
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I]
	CMP K, A
	JGE mat_add.endloop0

mat_add.start0:
	MOVW J:I, ptr [BP + 8]
	MOVW D:A, ptr [J:I + 4]
	PUSH D
	PUSH A
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I]
	MUL A, K
	MULH D:A, 2
	ADD A, word [SP]
	ADC D, word [SP + 2]
	ADD SP 4
	MOVW B:C, D:A
	MOVW ptr [BP + -4], J:I
	MOVW J:I, ptr [BP + 12]
	MOVW D:A, ptr [J:I + 4]
	MOVW J:I, ptr [BP + -4]
	PUSH D
	PUSH A
	MOVW ptr [BP + -4], J:I
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I]
	MOVW J:I, ptr [BP + -4]
	MUL A, K
	MULH D:A, 2
	ADD A, word [SP]
	ADC D, word [SP + 2]
	ADD SP 4
	MOVW J:I, D:A
	MOVW ptr [BP + -8], J:I
	MOVW J:I, ptr [BP + 16]
	MOVW D:A, ptr [J:I + 4]
	MOVW J:I, ptr [BP + -8]
	PUSH D
	PUSH A
	MOVW ptr [BP + -8], J:I
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I]
	MOVW J:I, ptr [BP + -8]
	MUL A, K
	MULH D:A, 2
	ADD A, word [SP]
	ADC D, word [SP + 2]
	ADD SP 4
	MOVW ptr [BP + -4], D:A
	MOV L, 0

mat_add.loop1:
	MOVW ptr [BP + -8], J:I
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I + 2]
	MOVW J:I, ptr [BP + -8]
	CMP L, A
	JGE mat_add.endloop1

mat_add.start1:
	MOVW ptr [BP + -8], J:I
	MOVW J:I, ptr [BP + -4]
	MOV A, L
	MULH D:A, 2
	ADD I, A
	ADC J, D
	MOVW ptr [BP + -12], J:I
	MOVW J:I, ptr [BP + -8]
	MOV A, L
	MULH D:A, 2
	ADD I, A
	ADC J, D
	PUSH word [J:I]
	MOVW J:I, ptr [BP + -12]
	MOVW J:I, B:C
	MOV A, L
	MULH D:A, 2
	ADD I, A
	ADC J, D
	MOV A, word [J:I]
	MOVW J:I, ptr [BP + -12]
	ADD A, word [SP]
	ADD SP 2
	MOV word [J:I], A
	MOVW J:I, ptr [BP + -8]

mat_add.next1:
	INC L
	JMP mat_add.loop1

mat_add.endloop1:
mat_add.next0:
	INC K
	JMP mat_add.loop0

mat_add.endloop0:
	MOV A 0

mat_add%epilogue:
	POP L
	POP K
	POP J
	POP I
	ADD SP byte 12
	POP BP
	RET

sca_add:
	PUSH BP
	MOVW BP, SP
	SUB SP byte 8
	PUSH I
	PUSH J
	PUSH K
	PUSH L
	MOVW J:I, ptr [BP + 14]
	PUSH word [J:I + 2]
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I + 2]
	CMP A, word [SP]
	CMOVNZ A, 1
	CMOVZ A, 0
	ADD SP 2
	PUSH A
	MOVW J:I, ptr [BP + 14]
	PUSH word [J:I]
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I]
	CMP A, word [SP]
	CMOVNZ A, 1
	CMOVZ A, 0
	ADD SP 2
	OR A, word [SP]
	ADD SP 2
	CMP 0 A
	JZ sca_add.endif0
	MOV A 1
	JMP sca_add%epilogue

sca_add.endif0:
	MOV K, 0

sca_add.loop0:
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I]
	CMP K, A
	JGE sca_add.endloop0

sca_add.start0:
	MOVW J:I, ptr [BP + 8]
	MOVW D:A, ptr [J:I + 4]
	PUSH D
	PUSH A
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I]
	MUL A, K
	MULH D:A, 2
	ADD A, word [SP]
	ADC D, word [SP + 2]
	ADD SP 4
	MOVW B:C, D:A
	MOVW ptr [BP + -4], J:I
	MOVW J:I, ptr [BP + 14]
	MOVW D:A, ptr [J:I + 4]
	MOVW J:I, ptr [BP + -4]
	PUSH D
	PUSH A
	MOVW ptr [BP + -4], J:I
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I]
	MOVW J:I, ptr [BP + -4]
	MUL A, K
	MULH D:A, 2
	ADD A, word [SP]
	ADC D, word [SP + 2]
	ADD SP 4
	MOVW J:I, D:A
	MOV L, 0

sca_add.loop1:
	MOVW ptr [BP + -4], J:I
	MOVW J:I, ptr [BP + 8]
	MOV A, word [J:I + 2]
	MOVW J:I, ptr [BP + -4]
	CMP L, A
	JGE sca_add.endloop1

sca_add.start1:
	MOVW ptr [BP + -4], J:I
	MOV A, L
	MULH D:A, 2
	ADD I, A
	ADC J, D
	MOVW ptr [BP + -8], J:I
	MOVW J:I, B:C
	MOV A, L
	MULH D:A, 2
	ADD I, A
	ADC J, D
	MOV A, word [J:I]
	MOVW J:I, ptr [BP + -8]
	ADD A, word [BP + 12]
	MOV word [J:I], A
	MOVW J:I, ptr [BP + -4]

sca_add.next1:
	INC L
	JMP sca_add.loop1

sca_add.next0:
sca_add.endloop1:
	INC K
	JMP sca_add.loop0

sca_add.endloop0:
	MOV A 0

sca_add%epilogue:
	POP L
	POP K
	POP J
	POP I
	ADD SP byte 8
	POP BP
	RET
