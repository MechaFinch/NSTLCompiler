
;
; Example Code (assembly edition)
; To highlight how bad the current compiler is
;
; Execution times
;	NSTL:	3,145,773 instructions
;	ASM:	  953,780 instructions
;

%libname example

%define COL_PRIME 0xFF
%define COL_COMP 0x00
%define SCREEN_START 0xF002_0000
%define SCREEN_SIZE_SQRT 278
%define SCREEN_SIZE (320 * 240)
%define SCREEN_END (SCREEN_START + SCREEN_SIZE)

main:
	; fill screen
	MOVW D:A, ((COL_PRIME * 0x0100_0000) | (COL_PRIME * 0x0001_0000) | (COL_PRIME * 0x0000_0100) | COL_PRIME)
	MOVW B:C, SCREEN_START
	MOV I, SCREEN_SIZE / 32
.fill_loop:
	MOVW [B:C + 0], D:A
	MOVW [B:C + 4], D:A
	MOVW [B:C + 8], D:A
	MOVW [B:C + 12], D:A
	MOVW [B:C + 16], D:A
	MOVW [B:C + 20], D:A
	MOVW [B:C + 24], D:A
	MOVW [B:C + 28], D:A
	
	ADD C, 32
	ICC B
	DEC I
	JNZ .fill_loop
	
	; clear 0 and 1
	MOV A, (COL_COMP * 0x0100) | COL_COMP
	MOV [SCREEN_START], A
	
	; sieve of eratosthenes
	MOV B, 1 ; p
	
	; find next prime
.find_loop:
	INC B
	CMP B, SCREEN_SIZE_SQRT
	JAE .done
	
	CMP byte [SCREEN_START + B], 0
	JZ .find_loop

	; use it to sieve
	MOV D, B		; p^2
	MULH C:D, B
.sieve_loop:
	; are we done
	CMP C, SCREEN_SIZE / 0x0001_0000
	;JA .find_loop 				; definitely above
	JB .sieve					; definitely below
	CMP D, SCREEN_SIZE & 0xFFFF	; upper equal
	JAE .find_loop
	
	; clear current composite
.sieve:
	MOV [SCREEN_START + C:D], AL
	
	; increment
	ADD D, B
	ICC C
	JMP .sieve_loop
	
.done:
	HLT
	JMP .done